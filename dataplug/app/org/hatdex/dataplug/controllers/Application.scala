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

import scala.concurrent.Future

class Application @Inject() (
    val messagesApi: MessagesApi,
    configuration: play.api.Configuration,
    socialProviderRegistry: SocialProviderRegistry,
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    dataPlugViewSet: DataPlugViewSet,
    dataPlugEndpointService: DataPlugEndpointService,
    syncerActorManager: DataplugSyncerActorManager,
    clock: Clock) extends SilhouettePhataAuthenticationController(silhouette, clock, configuration) {

  protected val logger = Logger(this.getClass)
  protected val provider = configuration.getString("service.name").getOrElse("").toLowerCase
  protected implicit val ioEC = IoExecutionContext.ioThreadPool

  def index(): Action[AnyContent] = UserAwareAction.async { implicit request =>
    logger.debug(s"Maybe user? ${request.identity}")
    request.identity.map { implicit user =>
      val eventualResult = for {
        variantChoices <- syncerActorManager.currentProviderApiVariantChoices(user, provider)(ioEC)
        apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(user.userId)
      } yield {
        if (apiEndpointStatuses.isEmpty) {
          logger.debug(s"Got choices to sign up for $variantChoices")
          processSignups(selectedVariants = variantChoices.map(_.copy(active = true))) map { _ =>
            Ok(dataPlugViewSet.signupComplete(socialProviderRegistry, Option(variantChoices)))
          }
        }
        else {
          Future.successful(Ok(dataPlugViewSet.disconnect(socialProviderRegistry, Some(variantChoices))))
        }
      }

      eventualResult.flatMap(r => r)
        .recover {
          case e =>
            logger.error(s"$provider API cannot be accessed: ${e.getMessage}. Redirecting to $provider OAuth service.", e)
            Redirect(org.hatdex.dataplug.controllers.routes.SocialAuthController.authenticate(provider))
        }
    } getOrElse {
      Future.successful(Ok(dataPlugViewSet.signIn(AuthForms.signinHatForm)))
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

