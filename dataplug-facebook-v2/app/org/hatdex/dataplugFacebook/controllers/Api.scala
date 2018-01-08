/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugFacebook.controllers

import com.google.inject.Inject
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.DataPlugNotableShareRequest
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataPlugNotablesService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import org.hatdex.dataplugFacebook.apiInterfaces.FacebookFeedUploadInterface
import org.joda.time.DateTime
import play.api.i18n.MessagesApi
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import play.api.{ Configuration, Logger }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Api @Inject() (
    messagesApi: MessagesApi,
    configuration: Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction,
    dataPlugEndpointService: DataPlugEndpointService,
    dataPlugNotablesService: DataPlugNotablesService,
    facebookFeedUpdateInterface: FacebookFeedUploadInterface,
    syncerActorManager: DataplugSyncerActorManager) extends Controller {

  val logger = Logger(this.getClass)

  val ioEC = IoExecutionContext.ioThreadPool

  def create: Action[DataPlugNotableShareRequest] = Action.async(BodyParsers.parse.json[DataPlugNotableShareRequest]) { implicit request =>
    request.headers.get("x-auth-token") map { secret =>
      val configuredSecret = configuration.getString("service.notables.secret").getOrElse("")

      if (secret == configuredSecret) {
        val notableShareRequest = request.body

        dataPlugNotablesService.find(notableShareRequest.notableId) flatMap { maybeNotableStatus =>
          if (!maybeNotableStatus.exists(_.posted)) {
            val sharedNotable = maybeNotableStatus.getOrElse(notableShareRequest.dataPlugSharedNotable)
            for {
              statusUpdate <- facebookFeedUpdateInterface.post(notableShareRequest.hatDomain, notableShareRequest)
              _ <- dataPlugNotablesService.save(sharedNotable.copy(posted = true, postedTime = Some(DateTime.now()), providerId = Some(statusUpdate.id)))
              mns <- dataPlugNotablesService.find(notableShareRequest.notableId)
            } yield {
              logger.info(s"Found inserted notable: $mns")
              Ok(Json.toJson(Map("message" -> "Notable accepted for posting")))
            }
          }
          else {
            Future.successful(BadRequest(generateResponseJson("Bad Request", "Notable already exists")))
          }
        }
      }
      else {
        Future.successful(Unauthorized(generateResponseJson("Unauthorized", "Authentication failed")))
      }
    } getOrElse {
      Future.successful(Unauthorized(generateResponseJson("Unauthorized", "Authentication token missing or malformed")))
    }
  }

  def delete(id: String): Action[AnyContent] = Action.async { implicit request =>
    request.headers.get("X-Auth-Token") map { secret =>
      val configuredSecret = configuration.getString("service.notables.secret").getOrElse("")

      if (secret == configuredSecret) {
        dataPlugNotablesService.find(id) flatMap {
          case Some(status) =>
            if (status.posted && !status.deleted && status.providerId.isDefined) {
              for {
                _ <- facebookFeedUpdateInterface.delete(status.phata, status.providerId.get)
                _ <- dataPlugNotablesService.save(status.copy(posted = false, deleted = true, deletedTime = Some(DateTime.now())))
              } yield {
                Ok(Json.toJson(Map("message" -> "Notable deleted.")))
              }
            }
            else if (status.posted && status.deleted) {
              Future.successful(BadRequest(generateResponseJson("Bad request", "Already deleted")))
            }
            else {
              Future.successful(BadRequest(generateResponseJson("Bad request", "Could not complete requested action")))
            }
          case None =>
            Future.successful(BadRequest(generateResponseJson("Bad request", "Notable not found")))
        }
      }
      else {
        Future.successful(Unauthorized(generateResponseJson("Unauthorized", "Authentication failed")))
      }
    } getOrElse {
      Future.successful(Unauthorized(generateResponseJson("Unauthorized", "Authentication token missing or malformed")))
    }
  }

  private def generateResponseJson(message: String, error: String): JsValue =
    Json.toJson(Map(
      "message" -> message,
      "error" -> error))
}