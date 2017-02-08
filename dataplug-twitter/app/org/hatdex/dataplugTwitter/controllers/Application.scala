/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugTwitter.controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.commonPlay.models.auth.forms.AuthForms
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.controllers.DataPlugViewSet
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
import org.hatdex.dataplugTwitter.{ views => dataplugTwitterViews }
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import org.hatdex.dataplug.models.User

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

  syncerActorManager.startAllActiveVariantChoices()

  def index: Action[AnyContent] = UserAwareAction.async { implicit request =>
    //    request.identity.get
    Logger.debug(s"Maybe user? ${request.identity}")
    request.identity.map { implicit user =>
      val eventualResult = for {
        variantChoices <- syncerActorManager.currentProviderApiVariantChoices(user, "twitter")(ioEC)
        apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(user.userId)
      } yield {
        if (apiEndpointStatuses.isEmpty) {
          val selectedVariants = variantChoices.map(_.key).toList
          processSignups(selectedVariants) map { _ =>
            Ok(dataplugTwitterViews.html.complete(socialProviderRegistry))
          }
        }
        else {
          Future.successful(Ok(dataplugTwitterViews.html.disconnect(socialProviderRegistry)))
        }
      }

      eventualResult.flatMap(r => r)
        .recover {
          case e =>
            Logger.debug(s"Twitter API cannot be accessed. Redirecting to Twitter OAuth service.")
            Redirect(org.hatdex.dataplug.controllers.routes.SocialAuthController.authenticate("twitter"))
        }
    } getOrElse {
      Future.successful(Ok(dataPlugViewSet.signIn(AuthForms.signinHatForm)))
    }
  }

  def processSignups: Action[AnyContent] = SecuredAction.async { implicit request =>
    dataPlugViewSet.asInstanceOf[DataPlugViewSetTwitter].variantsForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(dataPlugViewSet.signIn(AuthForms.signinHatForm))),
      selectedVariants => {
        processSignups(selectedVariants)
      })
  }

  private def processSignups(selectedVariants: List[String])(implicit user: User, requestHeader: RequestHeader) = {
    Logger.debug(s"Processing Variant Choices $selectedVariants")

    val eventualCurrentVariantChoices = FutureTransformations.transform {
      user.linkedUsers.find(_.providerId == "twitter") map { _ =>
        syncerActorManager.currentProviderApiVariantChoices(user, "twitter")(ioEC)
      }
    }

    eventualCurrentVariantChoices flatMap { maybeVariantChoices =>

      maybeVariantChoices map { variantChoices =>
        val signedUpVariants = variantChoices.map { variantChoice =>
          variantChoice.copy(active = selectedVariants.contains(variantChoice.key))
        }
        Logger.info(s"Api Variants info: ${signedUpVariants.map(vc => (vc.key, vc.active)).mkString("\n")}")

        syncerActorManager.updateApiVariantChoices(user, signedUpVariants).map { _ =>
          Redirect(dataPlugViewSet.indexRedirect)
            .flashing("success" -> "Changes have been successfully saved.")
        }
      } getOrElse {
        Future.successful(Redirect(dataPlugViewSet.indexRedirect)
          .flashing("error" -> "Synchronisation not possible - you have no options available right now."))
      }
    }
  }

}

