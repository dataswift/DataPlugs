/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplugCalendar.controllers

import javax.inject.{ Inject, Named }

import akka.actor.ActorRef
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.commonPlay.models.auth.forms.AuthForms
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.actors.DataPlugManagerActor.{ Start, Stop }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointVariant, ApiEndpointVariantChoice }
import org.hatdex.dataplug.controllers.DataPlugViewSet
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.services.DataPlugEndpointService
import org.hatdex.dataplug.utils.{ PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
import org.hatdex.dataplugCalendar.apiInterfaces.{ GoogleCalendarInterface, GoogleCalendarList }
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue
import play.api.mvc._

import scala.concurrent.Future

class Application @Inject() (
    val messagesApi: MessagesApi,
    configuration: play.api.Configuration,
    socialProviderRegistry: SocialProviderRegistry,
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    dataPlugViewSet: DataPlugViewSet,
    dataPlugEndpointService: DataPlugEndpointService,
    googleCalendarListApiInterface: GoogleCalendarList,
    googleCalendarInterface: GoogleCalendarInterface,
    @Named("dataPlugManager") dataPlugManagerActor: ActorRef,
    clock: Clock) extends SilhouettePhataAuthenticationController(silhouette, clock, configuration) {

  val ioEC = IoExecutionContext.ioThreadPool

  def index: Action[AnyContent] = UserAwareAction.async { implicit request =>
    //    request.identity.get
    Logger.debug(s"Maybe user? ${request.identity}")
    request.identity.map { implicit user =>
      val eventualVariants = user.linkedUsers.find(_.providerId == "google") map { _ =>
        availableApiVariantChoices(user)
      }

      FutureTransformations.transform(eventualVariants) map { variants =>
        Ok(dataPlugViewSet.connect(socialProviderRegistry, variants, dataPlugViewSet.variantsForm))
      }
    } getOrElse {
      Future.successful(Ok(dataPlugViewSet.signIn(AuthForms.signinHatForm)))
    }
  }

  private def availableApiVariantChoices(user: User): Future[Seq[ApiEndpointVariantChoice]] = {
    for {
      apiCall <- googleCalendarListApiInterface.buildFetchParameters(None)(ioEC)
      result <- googleCalendarListApiInterface.get(apiCall, user.userId, null)(ioEC)
      enabledVariants <- dataPlugEndpointService.enabledApiVariantChoices(user.userId)
    } yield {
      //      Logger.info(s"Got calendars for $user: $result")
      (result \ "items").as[Seq[JsValue]] map { calendar =>
        val calendarId = (calendar \ "id").as[String]
        val summary = (calendar \ "summary").as[String]
        val pathParameters = googleCalendarListApiInterface.defaultApiEndpoint.pathParameters + ("calendarId" -> calendarId)
        val variant = ApiEndpointVariant(
          ApiEndpoint("calendar", "Google Calendars", None),
          Some(calendarId), Some(summary),
          Some(googleCalendarInterface.defaultApiEndpoint.copy(pathParameters = pathParameters)))

        ApiEndpointVariantChoice(calendarId, summary,
          active = enabledVariants.exists(v => v.variant.variant == variant.variant),
          enabledVariants.find(_.key == calendarId).map(_.variant).getOrElse(variant))
      }
    }
  }

  def processSignups: Action[AnyContent] = SecuredAction.async { implicit request =>
    implicit val user = request.identity
    dataPlugViewSet.asInstanceOf[DataPlugViewSetCalendar].variantsForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(dataPlugViewSet.signIn(AuthForms.signinHatForm))),
      selectedVariants => {
        Logger.debug(s"Processing variantChoices $selectedVariants")
        val eventualMaybeVariantChoices = FutureTransformations.transform {
          user.linkedUsers.find(_.providerId == "google") map { _ =>
            availableApiVariantChoices(user)
          }
        }
        eventualMaybeVariantChoices flatMap { maybeVariantChoices =>
          maybeVariantChoices map { variantChoices =>
            val signedUpVariants = variantChoices.map { variantChoice =>
              variantChoice.copy(active = selectedVariants.contains(variantChoice.key))
            }
            Logger.info(s"Api Variants info: ${signedUpVariants.map(vc => (vc.key, vc.active)).mkString("\n")}")
            updateApiVariantChoices(user, signedUpVariants).map { _ =>
              Redirect(dataPlugViewSet.indexRedirect)
            }
          } getOrElse {
            Future.successful(NotFound(s"No options found for ${request.identity}"))
          }
        }
      })
  }

  private def updateApiVariantChoices(user: User, variantChoices: Seq[ApiEndpointVariantChoice]): Future[Unit] = {
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

}

