/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplugFitbit.controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.commonPlay.models.auth.forms.AuthForms
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.controllers.DataPlugViewSet
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
import org.hatdex.dataplugFitbit.{ views => fitbitViews }
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
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

  val ioEC = IoExecutionContext.ioThreadPool

  private val logger = Logger("ApplicationController")

  syncerActorManager.startAllActiveVariantChoices()

  def index: Action[AnyContent] = UserAwareAction.async { implicit request =>
    logger.debug(s"Maybe user? ${request.identity}")
    request.identity.map { implicit user =>
      val eventualResult = for {
        variantChoices <- syncerActorManager.currentProviderApiVariantChoices(user, "fitbit")(ioEC)
        apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(user.userId)
      } yield {
        logger.error(s"booo $variantChoices \n $apiEndpointStatuses")
        if (apiEndpointStatuses.isEmpty) {
          processSignups(selectedVariants = variantChoices.map(_.copy(active = true))) map { _ =>
            Ok(fitbitViews.html.complete(socialProviderRegistry))
          }
        }
        else {
          Future.successful(Ok(fitbitViews.html.disconnect(socialProviderRegistry)))
        }
      }

      eventualResult.flatMap(r => r)
        .recover {
          case e =>
            logger.debug("Fitbit API cannot be accessed. Redirecting to Twitter OAuth service.")
            Redirect(org.hatdex.dataplug.controllers.routes.SocialAuthController.authenticate("fitbit"))
        }
    }.getOrElse {
      Future.successful(Ok(dataPlugViewSet.signIn(AuthForms.signinHatForm)))
    }
  }

  private def processSignups(selectedVariants: Seq[ApiEndpointVariantChoice])(implicit user: User, requestHeader: RequestHeader): Future[Result] = {
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

