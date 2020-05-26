/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.controllers

import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.apiInterfaces.authProviders.HatOAuth2Provider
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointStatus, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.models.User
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager, HatTokenService, UserService }
import com.hubofallthings.dataplug.utils.{ PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
import javax.inject.Inject
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import com.nimbusds.jwt.SignedJWT
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class Application @Inject() (
    components: ControllerComponents,
    configuration: play.api.Configuration,
    socialProviderRegistry: SocialProviderRegistry,
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    dataPlugViewSet: DataPlugViewSet,
    dataPlugEndpointService: DataPlugEndpointService,
    syncerActorManager: DataplugSyncerActorManager,
    userService: UserService,
    hatTokenService: HatTokenService,
    hatProvider: HatOAuth2Provider,
    clock: Clock) extends SilhouettePhataAuthenticationController(components, silhouette, clock, configuration) {

  protected val logger: Logger = Logger(this.getClass)
  protected val provider: String = configuration.getOptional[String]("service.provider").getOrElse("").toLowerCase
  protected val chooseVariants: Boolean = configuration.getOptional[Boolean]("service.chooseVariants").getOrElse(false)
  protected implicit val ioEC: ExecutionContext = IoExecutionContext.ioThreadPool

  def signIn(): Action[AnyContent] = UserAwareAction { implicit request =>
    val signinHatForm = Form("hataddress" -> nonEmptyText)

    (request.queryString.get("token"), request.identity) match {
      case (_, Some(_)) => Redirect(dataPlugViewSet.indexRedirect)
      case (Some(Seq(accessToken)), None) =>
        val maybeSignedJWT = Try(SignedJWT.parse(accessToken))
        val maybeTokenVersionCorrect = maybeSignedJWT.map(_.getJWTClaimsSet.getClaims.containsKey("application"))

        if (maybeTokenVersionCorrect.isSuccess && !maybeTokenVersionCorrect.get) {
          Ok(dataPlugViewSet.signIn(signinHatForm, Some("Failed to login - HAT App version outdated. Please update and try again.")))
        }
        else {
          Ok(dataPlugViewSet.signIn(signinHatForm, Some("Failed to login. Please make sure you have the latest version of the HAT app and try again.")))
        }
      case (None, None) => Ok(dataPlugViewSet.signIn(signinHatForm, None))
    }
  }

  def index(): Action[AnyContent] = SecuredAction.async { implicit request =>
    val result = if (request.identity.linkedUsers.exists(_.providerId == provider)) {
      logger.info(s"Data provider already connected, proceeding with data synchronization")
      // If the required social profile is connected, proceed with syncing signup
      for {
        variantChoices <- syncerActorManager.currentProviderApiVariantChoices(request.identity, provider)(ioEC)
        apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(request.identity.userId)
        result <- if (chooseVariants) {
          Future.successful(handleUserWithChoice(variantChoices, apiEndpointStatuses))
        }
        else {
          handleUserFullSelection(variantChoices, apiEndpointStatuses)
        }
      } yield result
    }
    else {
      logger.info(s"Data provider is not setup, redirecting to authentication process")
      // otherwise redirect to the provider to sign up
      Future.successful(Redirect(com.hubofallthings.dataplug.controllers.routes.SocialAuthController.authenticate(provider)))
    }

    result
      .recover {
        case e =>
          // Assume that if any error has happened, it may be fixed by re-authenticating with the provider
          logger.error(s"Error occurred: ${e.getMessage}. Redirecting to $provider OAuth service.", e)
          Redirect(com.hubofallthings.dataplug.controllers.routes.SocialAuthController.authenticate(provider))
      }
  }

  protected def handleUserWithChoice(
    variantChoices: Seq[ApiEndpointVariantChoice],
    apiEndpointStatuses: Seq[ApiEndpointStatus])(implicit requestHeader: RequestHeader, user: User): Result = {
    logger.debug(s"Let user choose what to sync: $variantChoices")
    if (apiEndpointStatuses.isEmpty) {
      Ok(dataPlugViewSet.connect(socialProviderRegistry, Some(variantChoices), dataPlugViewSet.variantsForm))
    }
    else {
      Ok(dataPlugViewSet.disconnect(socialProviderRegistry, Some(variantChoices), chooseVariants))
    }
  }

  protected def handleUserFullSelection(
    variantChoices: Seq[ApiEndpointVariantChoice],
    apiEndpointStatuses: Seq[ApiEndpointStatus])(implicit request: RequestHeader, user: User): Future[Result] = {
    logger.debug(s"Process endpoint choices automatically: $variantChoices")

    val maybeRedirect = request.session.get("redirect")
    val requiresSetup = apiEndpointStatuses.isEmpty || variantChoices.forall(!_.active)
    (maybeRedirect, requiresSetup) match {
      case (Some(redirect), true) => syncerActorManager.updateApiVariantChoices(user, variantChoices.map(_.copy(active = true))) map { _ =>
        Redirect(redirect)
      }

      case (Some(redirect), false) => Future.successful(Redirect(redirect))

      case (None, true) => syncerActorManager.updateApiVariantChoices(user, variantChoices.map(_.copy(active = true))) map { _ =>
        Ok(dataPlugViewSet.signupComplete(socialProviderRegistry, Option(variantChoices)))
      }

      case (None, false) => Future.successful(Ok(dataPlugViewSet.disconnect(socialProviderRegistry, Some(variantChoices), chooseVariants)))
    }
  }

  def connectVariants(): Action[AnyContent] = SecuredAction.async { implicit request =>
    if (chooseVariants) {
      dataPlugViewSet.variantsForm.bindFromRequest.fold(
        formWithErrors => Future.successful(BadRequest(dataPlugViewSet.connect(socialProviderRegistry, None, formWithErrors))),
        selectedVariants => {
          logger.debug(s"Processing variantChoices $selectedVariants")
          request.identity.linkedUsers.find(_.providerId == provider) map { _ =>
            val eventualCurrentVariantChoices = syncerActorManager.currentProviderApiVariantChoices(request.identity, provider)(ioEC)
              .map {
                _.map { variantChoice =>
                  variantChoice.copy(active = selectedVariants.contains(variantChoice.key))
                }
              }

            eventualCurrentVariantChoices flatMap { variantChoices =>
              logger.debug(s"Processing signups $variantChoices")
              syncerActorManager.updateApiVariantChoices(request.identity, variantChoices) map { _ =>
                Ok(dataPlugViewSet.signupComplete(socialProviderRegistry, Option(variantChoices)))
              }
            }
          } getOrElse {
            Future.successful(Redirect(dataPlugViewSet.indexRedirect)
              .flashing("error" -> "Synchronisation not possible - you have no options available right now."))
          }
        })
    }
    else {
      Future.successful(Redirect(dataPlugViewSet.indexRedirect).flashing("error" -> "Selecting data to synchronise is not possible"))
    }
  }

  def deactivateEndpoints(): Action[AnyContent] = SecuredAction.async { implicit request =>
    val eventualResult = for {
      variantChoices <- syncerActorManager.currentProviderApiVariantChoices(request.identity, provider)(ioEC)
      apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(request.identity.userId)
    } yield {
      if (apiEndpointStatuses.nonEmpty) {
        logger.debug(s"Got choices to disconnect: $variantChoices")
        syncerActorManager.updateApiVariantChoices(request.identity, variantChoices.map(_.copy(active = false))) map { _ =>
          Ok(dataPlugViewSet.disconnect(socialProviderRegistry, None, chooseVariants))
        }
      }
      else {
        Future.successful(Ok(dataPlugViewSet.disconnect(socialProviderRegistry, None, chooseVariants)))
      }
    }

    eventualResult.flatMap(r => r)
      .recover {
        case e =>
          logger.error(s"$provider API cannot be accessed: ${e.getMessage}. Redirecting to $provider OAuth service.", e)
          Redirect(dataPlugViewSet.disconnectRedirect)
      }
  }

  def disconnect(): Action[AnyContent] = SecuredAction.async { implicit request =>
    val userId = request.identity.linkedUsers.map(_.userId).headOption.getOrElse("")
    val phata = request.identity.userId
    val serviceName = configuration.get[String]("service.name")
    logger.warn(s"User $phata attempts to disconnect $serviceName plug")

    for {
      redirect <- deactivateEndpoints()(request)
      _ <- userService.delete(phata, userId)
      _ <- hatTokenService.delete(phata)
      _ <- hatProvider.disconnect(phata, userId)
    } yield {
      redirect
    }
  }
}

