/*
 * Copyright (C) 2017 - 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugFacebook.controllers

import com.google.inject.Inject
import com.hubofallthings.dataplug.actors.Errors.SourceAuthenticationException
import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.apiInterfaces.models.DataPlugNotableShareRequest
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataPlugNotablesService, DataplugSyncerActorManager }
import com.hubofallthings.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import com.hubofallthings.dataplugFacebook.apiInterfaces.FacebookFeedUploadInterface
import org.joda.time.DateTime
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import play.api.{ Configuration, Logger }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Api @Inject() (
    components: ControllerComponents,
    configuration: Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction,
    dataPlugEndpointService: DataPlugEndpointService,
    dataPlugNotablesService: DataPlugNotablesService,
    facebookFeedUpdateInterface: FacebookFeedUploadInterface,
    syncerActorManager: DataplugSyncerActorManager) extends AbstractController(components) {

  val logger: Logger = Logger(this.getClass)

  val ioEC = IoExecutionContext.ioThreadPool

  def create: Action[DataPlugNotableShareRequest] = Action.async(parse.json[DataPlugNotableShareRequest]) { implicit request =>
    request.headers.get("x-auth-token") map { secret =>
      val configuredSecret = configuration.getOptional[String]("service.notables.secret").getOrElse("")

      if (secret == configuredSecret) {
        val notableShareRequest = request.body

        dataPlugNotablesService.find(notableShareRequest.notableId) flatMap { maybeNotableStatus =>
          // If notable status exists and the notable is posted - skip, all other cases - post
          if (!maybeNotableStatus.exists(_.posted)) {
            val sharedNotable = maybeNotableStatus.getOrElse(notableShareRequest.dataPlugSharedNotable)
            val eventualNotablePost = for {
              statusUpdate <- facebookFeedUpdateInterface.post(notableShareRequest.hatDomain, notableShareRequest)
              _ <- dataPlugNotablesService.save(sharedNotable.copy(posted = true, postedTime = Some(DateTime.now()), providerId = Some(statusUpdate.id)))
              mns <- dataPlugNotablesService.find(notableShareRequest.notableId)
            } yield {
              logger.info(s"Found inserted notable: $mns")
              Ok(Json.toJson(Map("message" -> "Notable accepted for posting")))
            }

            eventualNotablePost.recover {
              case e: SourceAuthenticationException =>
                logger.warn(s"HAT ${notableShareRequest.hatDomain} is not authorized with Facebook service. Failed to post ${notableShareRequest.notableId}. Details: ${e.getMessage}")
                Forbidden(generateResponseJson("Forbidden", s"${notableShareRequest.hatDomain} is not allowed to post to Facebook."))
              case e =>
                logger.error(s"Failed to post notable for ${notableShareRequest.hatDomain}. Notable ID: ${notableShareRequest.notableId}\nReason: ${e.getMessage}")
                InternalServerError(generateResponseJson("Internal Server Error", "The request cannot be completed at this time."))
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
      val configuredSecret = configuration.getOptional[String]("service.notables.secret").getOrElse("")

      if (secret == configuredSecret) {
        dataPlugNotablesService.find(id) flatMap {
          case Some(status) =>
            if (status.posted && !status.deleted && status.providerId.isDefined) {
              val eventualNotableDelete = for {
                _ <- facebookFeedUpdateInterface.delete(status.phata, status.providerId.get)
                _ <- dataPlugNotablesService.save(status.copy(posted = false, deleted = true, deletedTime = Some(DateTime.now())))
              } yield {
                Ok(Json.toJson(Map("message" -> "Notable deleted.")))
              }

              eventualNotableDelete.recover {
                case e =>
                  logger.error(s"Failed to delete notable. Notable ID: $id\nReason: ${e.getMessage}")
                  InternalServerError(generateResponseJson("Internal Server Error", "The request cannot be completed at this time."))
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

  private def generateResponseJson(error: String, message: String): JsValue =
    Json.toJson(Map(
      "message" -> message,
      "error" -> error))
}