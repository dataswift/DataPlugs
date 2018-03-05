/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.apiInterfaces

import akka.actor.ActorRef
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticator
import org.hatdex.dataplug.apiInterfaces.models._
import play.api.http.Status._
import play.api.libs.json.JsValue

import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugOptionsCollector extends RequestAuthenticator with DataPlugApiEndpointClient {
  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef, retrying: Boolean)(implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    val authenticatedFetchParameters = authenticateRequest(fetchParams, hatAddress, refreshToken = retrying)

    authenticatedFetchParameters flatMap { requestParameters =>
      buildRequest(requestParameters)
    } flatMap { result =>
      result.status match {
        case OK =>
          Future.successful(generateEndpointChoices(Some(result.json)))

        case UNAUTHORIZED =>
          if (!retrying) {
            logger.debug(s"Unauthorized request $fetchParams for $hatAddress - ${result.status}: ${result.body}")
            get(fetchParams, hatAddress, hatClientActor, retrying = true)
          }
          else {
            logger.warn(s"Unauthorized request after retrying $fetchParams for $hatAddress - ${result.status}: ${result.body}")
            Future.failed(new RuntimeException(s"Unauthorized request after retrying $fetchParams for $hatAddress - ${result.status}: ${result.body}"))
          }

        case _ =>
          logger.warn(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}")
          Future.failed(new RuntimeException(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}"))
      }
    } recoverWith {
      case e =>
        logger.warn(s"Error when querying api endpoint $fetchParams - ${e.getMessage}")
        Future.failed(e)
    }
  }

  def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice]
}
