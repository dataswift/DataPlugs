/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.controllers

import javax.inject.{ Inject, Named }

import akka.actor.ActorRef
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.commonPlay.models.auth.forms.AuthForms
import org.hatdex.dataplug.actors.DataPlugManagerActor.{ Start, Stop }
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.services.DataPlugEndpointService
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
    @Named("dataPlugManager") dataPlugManagerActor: ActorRef,
    clock: Clock) extends SilhouettePhataAuthenticationController(silhouette, clock, configuration) {

  def index(): Action[AnyContent] = UserAwareAction.async { implicit request =>
    Logger.debug(s"Maybe user? ${request.identity}")
    request.identity.map { implicit user =>
      Future.successful(Ok(dataPlugViewSet.connect(socialProviderRegistry, None, dataPlugViewSet.variantsForm)))
    } getOrElse {
      Future.successful(Ok(dataPlugViewSet.signIn(AuthForms.signinHatForm)))
    }
  }

  protected def updateApiVariantChoices(user: User, variantChoices: Seq[ApiEndpointVariantChoice])(implicit ec: ExecutionContext): Future[Unit] = {
    dataPlugEndpointService.updateApiVariantChoices(user.userId, variantChoices) map {
      case _ =>
        variantChoices foreach { variantChoice =>
          if (variantChoice.active) {
            dataPlugManagerActor ! Start(variantChoice.variant, user.userId, variantChoice.variant.configuration)
          }
          else {
            dataPlugManagerActor ! Stop(variantChoice.variant, user.userId)
          }
        }
    }
  }

  protected def startAllActiveVariantChoices()(implicit ec: ExecutionContext): Future[Unit] = {
    Logger.info("Starting active API endpoint syncing")
    dataPlugEndpointService.retrieveAllEndpoints map { phataVariants =>
      Logger.info(s"Retrieved endpoints to sync: ${phataVariants.mkString("\n")}")
      phataVariants foreach {
        case (phata, variant) =>
          dataPlugManagerActor ! Start(variant, phata, variant.configuration)
      }
    } recoverWith {
      case e =>
        Logger.error(s"Could not retrieve endpoints to sync: ${e.getMessage}")
        Future.failed(e)
    }
  }

}

