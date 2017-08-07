/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplugMonzo.controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.commonPlay.models.auth.forms.AuthForms
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.controllers.DataPlugViewSet
import org.hatdex.dataplug.services.DataplugSyncerActorManager
import org.hatdex.dataplug.utils.{ PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
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
    syncerActorManager: DataplugSyncerActorManager,
    clock: Clock) extends SilhouettePhataAuthenticationController(silhouette, clock, configuration) {

  val ioEC = IoExecutionContext.ioThreadPool

  syncerActorManager.startAllActiveVariantChoices()

  def index: Action[AnyContent] = UserAwareAction.async { implicit request =>
    //    request.identity.get
    Logger.debug(s"Maybe user? ${request.identity}")
    request.identity.map { implicit user =>
      val eventualCurrentVariantChoices = FutureTransformations.transform {
        user.linkedUsers.find(_.providerId == "monzo") map { _ =>
          syncerActorManager.currentProviderApiVariantChoices(user, "monzo")(ioEC)
        }
      }

      eventualCurrentVariantChoices map { variantChoices =>
        Ok(dataPlugViewSet.connect(socialProviderRegistry, variantChoices, dataPlugViewSet.variantsForm))
      } recover {
        case e =>
          Ok(dataPlugViewSet.connect(socialProviderRegistry, None, dataPlugViewSet.variantsForm))
      }
    } getOrElse {
      Future.successful(Ok(dataPlugViewSet.signIn(AuthForms.signinHatForm)))
    }
  }

  def processSignups: Action[AnyContent] = SecuredAction.async { implicit request =>
    implicit val user = request.identity
    dataPlugViewSet.asInstanceOf[DataPlugViewSetMonzo].variantsForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(dataPlugViewSet.signIn(AuthForms.signinHatForm))),
      selectedVariants => {
        Logger.debug(s"Processing variantChoices $selectedVariants")
        val eventualCurrentVariantChoices = FutureTransformations.transform {
          user.linkedUsers.find(_.providerId == "monzo") map { _ =>
            syncerActorManager.currentProviderApiVariantChoices(user, "monzo")(ioEC)
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
      })
  }

}

