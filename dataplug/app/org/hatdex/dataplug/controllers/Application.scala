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
import org.hatdex.commonPlay.models.auth.forms.AuthForms
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

class Application @Inject() (
    val messagesApi: MessagesApi,
    configuration: play.api.Configuration,
    socialProviderRegistry: SocialProviderRegistry,
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    dataPlugViewSet: DataPlugViewSet,
    dataPlugEndpointService: DataPlugEndpointService,
    syncerActorManager: DataplugSyncerActorManager,
    clock: Clock) extends SilhouettePhataAuthenticationController(silhouette, clock, configuration) {

  protected val logger: Logger = Logger(this.getClass)
  protected val provider: String = configuration.getString("service.name").getOrElse("").toLowerCase
  protected val chooseVariants: Boolean = configuration.getBoolean("service.chooseVariants").getOrElse(false)
  protected implicit val ioEC: ExecutionContext = IoExecutionContext.ioThreadPool

  def signIn(): Action[AnyContent] = UserAwareAction { implicit request =>
    request.identity map { _ =>
      Redirect(dataPlugViewSet.indexRedirect)
    } getOrElse {
      Ok(dataPlugViewSet.signIn(AuthForms.signinHatForm))
    }
  }

  def index(): Action[AnyContent] = SecuredAction.async { implicit request =>
    val eventualResult = if (request.identity.linkedUsers.exists(_.providerId == provider)) {
      // If the required social profile is connected, proceed with syncing signup
      for {
        variantChoices <- syncerActorManager.currentProviderApiVariantChoices(request.identity, provider)(ioEC)
        apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(request.identity.userId)
      } yield {
        if (chooseVariants) {
          logger.debug(s"Let user choose what to sync: $variantChoices")
          if (apiEndpointStatuses.isEmpty) {
            Future.successful(Ok(dataPlugViewSet.connect(socialProviderRegistry, Some(variantChoices), dataPlugViewSet.variantsForm)))
          }
          else {
            Future.successful(Ok(dataPlugViewSet.disconnect(socialProviderRegistry, Some(variantChoices), chooseVariants)))
          }
        }
        else {
          logger.debug(s"Process endpoint choices automatically: $variantChoices")
          if (apiEndpointStatuses.isEmpty || variantChoices.forall(!_.active)) {
            logger.debug(s"Got choices to sign up for $variantChoices")
            processSignups(selectedVariants = variantChoices.map(_.copy(active = true))) map { _ =>
              Ok(dataPlugViewSet.signupComplete(socialProviderRegistry, Option(variantChoices)))
            }
          }
          else {
            Future.successful(Ok(dataPlugViewSet.disconnect(socialProviderRegistry, Some(variantChoices), chooseVariants)))
          }
        }
      }
    }
    else {
      // otherwise redirect to the provider to sign up
      Future.successful(Future.successful(Redirect(org.hatdex.dataplug.controllers.routes.SocialAuthController.authenticate(provider))))
    }

    eventualResult.flatMap(r => r)
      .recover {
        case e =>
          // Assume that if any error has happened, it may be fixed by re-authenticating with the provider
          logger.error(s"Error occurred: ${e.getMessage}. Redirecting to $provider OAuth service.", e)
          Redirect(org.hatdex.dataplug.controllers.routes.SocialAuthController.authenticate(provider))
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
              processSignups(variantChoices) map { _ =>
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
        processSignups(selectedVariants = variantChoices.map(_.copy(active = false))) map { _ =>
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

  protected def processSignups(selectedVariants: Seq[ApiEndpointVariantChoice])(implicit user: User, requestHeader: RequestHeader): Future[Result] = {
    logger.debug(s"Processing Variant Choices $selectedVariants for user ${user.userId}")

    if (selectedVariants.nonEmpty) {
      syncerActorManager.updateApiVariantChoices(user, selectedVariants).map { _ =>
        Redirect(dataPlugViewSet.indexRedirect)
          .flashing("success" -> "Changes have been successfully saved.")
      }
    }
    else {
      Future.successful(Redirect(dataPlugViewSet.indexRedirect)
        .flashing("error" -> "Synchronisation not possible - you have no options available right now."))
    }

  }

}

