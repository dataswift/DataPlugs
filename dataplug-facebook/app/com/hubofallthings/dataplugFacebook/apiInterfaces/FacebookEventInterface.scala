/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 5, 2017
 */

package com.hubofallthings.dataplugFacebook.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import com.google.inject.Inject
import com.hubofallthings.dataplug.actors.Errors.SourceDataProcessingException
import com.hubofallthings.dataplug.apiInterfaces.DataPlugEndpointInterface
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import com.hubofallthings.dataplugFacebook.models.FacebookEvent
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.hubofallthings.dataplugFacebook.apiInterfaces.authProviders._
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FacebookEventInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FacebookProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "facebook"
  val endpoint: String = "events"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = FacebookEventInterface.defaultApiEndpoint

  val refreshInterval = 6.hours

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val maybeNextCursor = (content \ "paging" \ "next").asOpt[String]
      .flatMap(nextPage => Uri(nextPage).query().get("after"))
    val maybeSinceParam = params.pathParameters.get("since")

    maybeNextCursor.map { afterCursor =>
      logger.debug(s"Found next page cursor (continuing sync): $afterCursor")
      val updatedQueryParams = params.queryParameters + ("after" -> afterCursor)

      logger.debug(s"Updated query parameters: $updatedQueryParams")

      if (maybeSinceParam.isDefined) {
        logger.debug("'Since' parameter already set, updating query params")
        params.copy(queryParameters = updatedQueryParams)
      }
      else {
        val maybeNewSinceParam = for {
          dataArray <- (content \ "data").asOpt[JsArray]
          headItem <- dataArray.value.headOption
          latestUpdateTime <- (headItem \ "start_time").asOpt[String]
        } yield latestUpdateTime

        maybeNewSinceParam.map { newSinceParam =>
          logger.debug(s"Updating query params and setting 'since': $newSinceParam")
          params.copy(pathParameters = params.pathParameters + ("since" -> newSinceParam), queryParameters = updatedQueryParams)
        }.getOrElse {
          logger.warn("Unexpected API behaviour: 'since' not set and it was not possible to extract it from response body")
          params.copy(queryParameters = updatedQueryParams)
        }
      }
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    val maybeSinceParam = params.pathParameters.get("since")
    val updatedQueryParams = params.queryParameters - "after"

    logger.debug(s"Updated query parameters: $updatedQueryParams")

    maybeSinceParam.map { sinceParam =>
      logger.debug(s"Building next sync parameters $updatedQueryParams with 'since': $sinceParam")
      params.copy(pathParameters = params.pathParameters - "since", queryParameters = updatedQueryParams + ("since" -> sinceParam))
    }.getOrElse {
      logger.debug("'Since' parameter not found (likely no continuation runs), setting one now")

      val maybeNewSinceParam = for {
        dataArray <- (content \ "data").asOpt[JsArray]
        headItem <- dataArray.value.headOption
        latestUpdateTime <- (headItem \ "start_time").asOpt[String]
      } yield latestUpdateTime

      maybeNewSinceParam.map { newSinceParam =>
        logger.debug(s"Building next sync parameters $updatedQueryParams with 'since': $newSinceParam")
        params.copy(queryParameters = updatedQueryParams + ("since" -> newSinceParam))
      }.getOrElse {
        logger.warn("Could not extract latest event update time,'since' field is not set. Was the events list empty?")
        params
      }
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content, hatAddress))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  override def validateMinDataStructure(rawData: JsValue, hatAddress: String): Try[JsArray] = {
    (rawData \ "data").toOption.map {
      case data: JsArray if data.validate[List[FacebookEvent]].isSuccess =>
        logger.info(s"[$hatAddress] Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsArray =>
        logger.warn(s"[$hatAddress] Could not validate full item list. Parsing ${data.value.length} data items one by one.")
        Success(JsArray(data.value.filter(_.validate[FacebookEvent].isSuccess)))
      case data: JsObject =>
        logger.error(s"[$hatAddress] Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"[$hatAddress] Error parsing JSON object: ${data.validate[List[FacebookEvent]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"[$hatAddress] Error parsing JSON object: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }

}

object FacebookEventInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v5.0",
    "/me/events",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "250", "pretty" -> "0"),
    Map(),
    Some(Map()))
}
