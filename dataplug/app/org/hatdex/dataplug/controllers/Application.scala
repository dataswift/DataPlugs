/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointStatus, ApiEndpointVariantChoice }
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

class Application @Inject() (
    components: ControllerComponents,
    configuration: play.api.Configuration,
    socialProviderRegistry: SocialProviderRegistry,
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    dataPlugViewSet: DataPlugViewSet,
    dataPlugEndpointService: DataPlugEndpointService,
    syncerActorManager: DataplugSyncerActorManager,
    clock: Clock) extends SilhouettePhataAuthenticationController(components, silhouette, clock, configuration) {

  protected val logger: Logger = Logger(this.getClass)
  protected val provider: String = configuration.get[Option[String]]("service.provider").getOrElse("").toLowerCase
  protected val chooseVariants: Boolean = configuration.get[Option[Boolean]]("service.chooseVariants").getOrElse(false)
  protected implicit val ioEC: ExecutionContext = IoExecutionContext.ioThreadPool

  def signIn(): Action[AnyContent] = UserAwareAction { implicit request =>
    val signinHatForm = Form("hataddress" -> nonEmptyText)

    request.identity map { _ =>
      Redirect(dataPlugViewSet.indexRedirect)
    } getOrElse {
      Ok(dataPlugViewSet.signIn(signinHatForm))
    }
  }

  def index(): Action[AnyContent] = SecuredAction.async { implicit request =>
    val result = if (request.identity.linkedUsers.exists(_.providerId == provider)) {
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
      // otherwise redirect to the provider to sign up
      Future.successful(Redirect(org.hatdex.dataplug.controllers.routes.SocialAuthController.authenticate(provider)))
    }

    result
      .recover {
        case e =>
          // Assume that if any error has happened, it may be fixed by re-authenticating with the provider
          logger.error(s"Error occurred: ${e.getMessage}. Redirecting to $provider OAuth service.", e)
          Redirect(org.hatdex.dataplug.controllers.routes.SocialAuthController.authenticate(provider))
      }
  }

  private def handleUserWithChoice(
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
    if (apiEndpointStatuses.isEmpty || variantChoices.forall(!_.active)) {
      logger.debug(s"Got choices to sign up for $variantChoices")
      syncerActorManager.updateApiVariantChoices(user, variantChoices.map(_.copy(active = true))) map { _ =>
        // Automatically redirect the user if there is a redirect registered in the session
        request.session.get("redirect").map { r =>
          Redirect(r)
        } getOrElse {
          Ok(dataPlugViewSet.signupComplete(socialProviderRegistry, Option(variantChoices)))
        }
      }
    }
    else {
      Future.successful(Ok(dataPlugViewSet.disconnect(socialProviderRegistry, Some(variantChoices), chooseVariants)))
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

  def disconnect(): Action[AnyContent] = SecuredAction.async { implicit request =>
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
          Redirect(org.hatdex.dataplug.controllers.routes.SocialAuthController.authenticate(provider))
      }
  }

}

