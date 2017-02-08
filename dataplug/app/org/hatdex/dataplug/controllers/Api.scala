/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.controllers

import javax.inject.{ Inject, Named }

import akka.actor.{ ActorRef, ActorSystem }
import org.hatdex.dataplug.apiInterfaces.models.JsonProtocol
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.json.{ JsError, Json }
import play.api.libs.ws.WSClient
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

  def tickle: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    syncerActorManager.runPhataActiveVariantChoices(request.identity.userId) map {
      case _ =>
        Ok(Json.toJson(Map("message" -> "Tickled")))
    }
  }

  import JsonProtocol.endpointStatusFormat
  def status: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    dataPlugEndpointService.listCurrentEndpointStatuses(request.identity.userId) map { apiEndpointStatuses =>
      Ok(Json.toJson(apiEndpointStatuses))
    }
  }

  def permissions: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    Future.successful(InternalServerError(Json.toJson(Map("message" -> "Not Implemented", "error" -> "Not implemented"))))
  }

}
