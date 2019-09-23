/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.controllers

import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.models.User
import com.hubofallthings.dataplug.apiInterfaces.models.JsonProtocol.endpointStatusFormat
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager, HatTokenService }
import com.hubofallthings.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import javax.inject.Inject
import com.nimbusds.jwt.SignedJWT
import org.hatdex.hat.api.models.ErrorMessage
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.libs.json.{ JsArray, JsValue, Json }
import play.api.{ Configuration, Logger }
import play.api.mvc._
import org.hatdex.hat.api.json.HatJsonFormats.errorMessage
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class Api @Inject() (
    components: ControllerComponents,
    cache: AsyncCacheApi,
    messagesApi: MessagesApi,
    configuration: Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction,
    dataPlugEndpointService: DataPlugEndpointService,
    hatTokenService: HatTokenService,
    syncerActorManager: DataplugSyncerActorManager) extends AbstractController(components) {

  protected val ioEC: ExecutionContext = IoExecutionContext.ioThreadPool
  protected val provider: String = configuration.getOptional[String]("service.provider").getOrElse("")

  protected val logger: Logger = Logger(this.getClass)

  def tickle: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    val cachedToken = cache.getOrElseUpdate(s"token:${request.identity.userId}", 1.hour) {
      hatTokenService.forUser(request.identity.userId)
    }

    cachedToken.flatMap { maybeAccessCredentials =>
      maybeAccessCredentials.map { accessCredentials =>
        syncerActorManager.runPhataActiveVariantChoices(accessCredentials.hat, accessCredentials.accessToken) map { _ =>
          Ok(Json.toJson(Map("message" -> "Tickled")))
        }
      }.getOrElse {
        Future.successful(Forbidden(Json.toJson(ErrorMessage("Forbidden", "Account is inactive"))))
      }
    }
  }

  def status: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    val token = request.headers.get("X-Auth-Token").get

    // Check if the user has the required social profile linked
    Try(SignedJWT.parse(token)).map { parsedToken =>
      request.identity.linkedUsers.find(_.providerId == provider) map { _ =>
        val tokenIssueDate = new DateTime(parsedToken.getJWTClaimsSet.getIssueTime)
        val eventualTokenInsertOrUpdate = hatTokenService.save(request.identity.userId, token, tokenIssueDate)

        eventualTokenInsertOrUpdate.flatMap {
          case Left(_) =>
            logger.info(s"Registered new HAT token for ${request.identity.userId}")
            Future.successful(Ok(Json.toJson(JsArray())))
          case Right(_) =>
            for {
              choices <- syncerActorManager.currentProviderApiVariantChoices(request.identity, provider)(ioEC) if choices.exists(_.active)
              apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(request.identity.userId)
              _ <- syncerActorManager.runPhataActiveVariantChoices(request.identity.userId, token)
            } yield {
              logger.info(s"Refreshed HAT token for ${request.identity.userId}")
              Ok(Json.toJson(apiEndpointStatuses))
            }
        }.recover {
          // In case fetching current endpoint statuses failed, assume the issue came from refreshing data from the provider
          // Also catches any failures related to HAT token saving
          case _ => Forbidden(
            Json.toJson(ErrorMessage(
              "Forbidden",
              "The user is not authorized to access remote data - has Access Token been revoked?")))
        }
      } getOrElse {
        Future.successful(Forbidden(Json.toJson(ErrorMessage("Forbidden", s"Required social profile ($provider) not connected"))))
      }
    }.get
  }

  def permissions: Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    Future.successful(InternalServerError(Json.toJson(Map("message" -> "Not Implemented", "error" -> "Not implemented"))))
  }

  def adminDisconnect(hat: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    val adminSecret = configuration.getOptional[String]("service.admin.secret").getOrElse("")

    (request.headers.get("x-auth-token"), hat) match {
      case (Some(authToken), Some(hatDomain)) =>
        if (authToken == adminSecret) {
          val eventualResult = for {
            variantChoices <- syncerActorManager.currentProviderStaticApiVariantChoices(hatDomain, provider)(ioEC)
            apiEndpointStatuses <- dataPlugEndpointService.listCurrentEndpointStatuses(hatDomain)
          } yield {
            if (apiEndpointStatuses.nonEmpty) {
              logger.debug(s"Got choices for $hatDomain to disconnect: $variantChoices")
              syncerActorManager.updateApiVariantChoices(User("", hatDomain, List()), variantChoices.map(_.copy(active = false))) map { _ =>
                Ok(Json.obj("message" -> s"Plug disconnected for $hatDomain"))
              }
            }
            else {
              Future.successful(BadRequest(jsonErrorResponse("Bad Request", s"Plug already disconnected for $hatDomain")))
            }
          }

          eventualResult.flatMap(r => r).recover {
            case e =>
              logger.error(s"$provider API cannot be accessed: ${e.getMessage}", e)
              BadRequest(jsonErrorResponse("Bad Request", s"Cannot find information for $hatDomain"))
          }
        }
        else {
          Future.successful(Unauthorized(jsonErrorResponse("Unauthorized", "Authentication token invalid")))
        }
      case (None, _) =>
        Future.successful(BadRequest(jsonErrorResponse("Bad Request", "Authentication token missing")))
      case (Some(_), None) =>
        Future.successful(BadRequest(jsonErrorResponse("Bad Request", "HAT address not specified")))
    }
  }

  private def jsonErrorResponse(error: String, message: String): JsValue =
    Json.obj("error" -> error, "message" -> message)
}
