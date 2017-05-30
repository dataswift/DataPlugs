/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplugCalendar.controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollectorRegistry
import org.hatdex.dataplug.apiInterfaces.models.JsonProtocol
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Api @Inject() (
    messagesApi: MessagesApi,
    configuration: play.api.Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction,
    dataPlugEndpointService: DataPlugEndpointService,
    syncerActorManager: DataplugSyncerActorManager) extends Controller {

  val ioEC = IoExecutionContext.ioThreadPool

  import JsonProtocol.endpointStatusFormat
  def status: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    // Check if the user has the required social profile linked
    request.identity.linkedUsers.find(_.providerId == "google") map {
      case _ =>
        val result = for {
          _ <- syncerActorManager.currentProviderApiVariantChoices(request.identity, "google")(ioEC)
          apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(request.identity.userId)
        } yield {
          Ok(Json.toJson(apiEndpointStatuses))
        }

        // In case fetching current endpoint statuses failed, assume the issue came from refreshing data from the provider
        result recover {
          case e =>
            Forbidden(
              Json.toJson(Map(
                "message" -> "Forbidden",
                "error" -> "The user is not authorized to access remote data - has Access Token been revoked?")))
        }
    } getOrElse {
      Future.successful(
        Forbidden(
          Json.toJson(Map(
            "message" -> "Forbidden",
            "error" -> "Required social profile not connected"))))
    }
  }

}
