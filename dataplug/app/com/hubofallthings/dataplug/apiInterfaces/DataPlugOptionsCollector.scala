/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.apiInterfaces

import akka.actor.ActorRef
import com.hubofallthings.dataplug.apiInterfaces.authProviders.RequestAuthenticator
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointVariantChoice }
import play.api.http.Status._
import play.api.libs.json.JsValue
import play.api.libs.ws.WSResponse

import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugOptionsCollector extends RequestAuthenticator with DataPlugApiEndpointClient {
  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef, retrying: Boolean)
         (implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {

    authenticateRequest(fetchParams, hatAddress, refreshToken = retrying).flatMap(buildRequest).flatMap { result =>
      result.status match {
        case OK =>
          Future.successful(generateEndpointChoices(Some(result.json)))

        case UNAUTHORIZED =>
          unauthorizedResponse(fetchParams, hatAddress, hatClientActor, retrying, result)

        case _ =>
          val errorMessage = s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}"
          error(errorMessage, new RuntimeException(errorMessage))
      }
    } recoverWith {
      case e =>
        error(s"Error when querying api endpoint $fetchParams - ${e.getMessage}", e)
    }
  }

  protected def error(errorMessage: String, exception: Throwable): Future[Nothing] = {
    logger.warn(errorMessage)
    Future.failed(exception)
  }

  protected def unauthorizedResponse(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef, retrying: Boolean, response: WSResponse)
                          (implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {

    if (!retrying) {
      logger.debug(s"Unauthorized request $fetchParams for $hatAddress - ${response.status}: ${response.body}")
      get(fetchParams, hatAddress, hatClientActor, retrying = true)
    }
    else {
      val errorMessage = s"Unauthorized request after retrying $fetchParams for $hatAddress - ${response.status}: ${response.body}"
      error(errorMessage, new RuntimeException(errorMessage))
    }
  }

  def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice]
}
