/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.controllers

import javax.inject.Inject

import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.JsonProtocol
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import org.hatdex.hat.api.models.ErrorMessage
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

  protected val ioEC = IoExecutionContext.ioThreadPool
  protected val provider = configuration.getString("service.name").getOrElse("")

  def tickle: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    syncerActorManager.runPhataActiveVariantChoices(request.identity.userId) map { _ =>
      Ok(Json.toJson(Map("message" -> "Tickled")))
    }
  }

  import JsonProtocol.endpointStatusFormat
  import org.hatdex.hat.api.json.HatJsonFormats.errorMessage
  def status: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    // Check if the user has the required social profile linked
    request.identity.linkedUsers.find(_.providerId == provider) map { _ =>
      val result = for {
        _ <- syncerActorManager.currentProviderApiVariantChoices(request.identity, provider)(ioEC)
        apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(request.identity.userId)
      } yield {
        Ok(Json.toJson(apiEndpointStatuses))
      }

      // In case fetching current endpoint statuses failed, assume the issue came from refreshing data from the provider
      result recover {
        case _ => Forbidden(
          Json.toJson(ErrorMessage(
            "Forbidden",
            "The user is not authorized to access remote data - has Access Token been revoked?")))
      }
    } getOrElse {
      Future.successful(Forbidden(Json.toJson(ErrorMessage("Forbidden", s"Required social profile ($provider) not connected"))))
    }
  }

  def permissions: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    Future.successful(InternalServerError(Json.toJson(Map("message" -> "Not Implemented", "error" -> "Not implemented"))))
  }

}
