/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugInstagram.apiInterfaces

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
import com.hubofallthings.dataplugInstagram.models.InstagramMedia
import com.hubofallthings.dataplugInstagram.apiInterfaces.authProviders.InstagramProvider
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import org.joda.time.DateTime
import play.api.{ Configuration, Logger }
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class InstagramFeedInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val configuration: Configuration,
    val provider: InstagramProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "instagram"
  val endpoint: String = "feed"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = InstagramFeedInterface.defaultApiEndpoint

  val refreshInterval = 1.hour

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    logger.debug(s"Content is $content")

    val maybeNextPage = (content \ "paging" \ "next").asOpt[String]
    val maybeSinceParam = params.pathParameters.get("since")

    logger.debug(s"Found possible next page link: $maybeNextPage")
    logger.debug(s"Found possible next since parameter: $maybeSinceParam")

    maybeNextPage.map { nextPage =>
      logger.debug(s"Found next page link (continuing sync): $nextPage")

      val nextPageUri = Uri(nextPage)
      val updatedQueryParams = params.queryParameters ++ nextPageUri.query().toMap

      logger.debug(s"Updated query parameters: $updatedQueryParams")

      if (maybeSinceParam.isDefined) {
        logger.debug("\"Since\" parameter already set, updating query params")
        params.copy(queryParameters = updatedQueryParams)
      }
      else {
        (content \ "paging" \ "previous").asOpt[String].flatMap { previousPage =>
          val previousPageUri = Uri(previousPage)
          previousPageUri.query().get("since").map { sinceParam =>
            val updatedPathParams = params.pathParameters + ("since" -> sinceParam)

            logger.debug(s"Updating query params and setting 'since': $sinceParam")
            params.copy(pathParameters = updatedPathParams, queryParameters = updatedQueryParams)
          }
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
    val updatedQueryParams = params.queryParameters - "__paging_token" - "until" - "access_token"

    logger.debug(s"Updated query parameters: $updatedQueryParams")

    maybeSinceParam.map { sinceParameter =>
      logger.debug(s"Building next sync parameters $updatedQueryParams with 'since': $sinceParameter")
      params.copy(pathParameters = params.pathParameters - "since", queryParameters = updatedQueryParams + ("since" -> sinceParameter))
    }.getOrElse {
      val maybePreviousPage = (content \ "paging" \ "previous").asOpt[String]

      logger.debug("'Since' parameter not found (likely no continuation runs), setting one now")
      maybePreviousPage.flatMap { previousPage =>
        Uri(previousPage).query().get("since").map { newSinceParam =>
          params.copy(queryParameters = params.queryParameters + ("since" -> newSinceParam))
        }
      }.getOrElse {
        logger.warn("Could not extract previous page 'since' parameter so the new value is not set. Was the feed list empty?")
        params
      }
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    val dataValidation =
      transformData(content)
        .map(validateMinDataStructure)
        .getOrElse(Failure(SourceDataProcessingException("Source data malformed, could not insert date in to the structure")))

    for {
      validatedData <- FutureTransformations.transform(dataValidation)
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully uploaded ${validatedData.value.length} new records to $hatAddress HAT")
      Done
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "data").toOption.map {
      case data: JsArray if data.validate[List[InstagramMedia]].isSuccess =>
        logger.info(s"Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"Error parsing JSON object: ${data.validate[List[InstagramMedia]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object, necessary property not found: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object, necessary property not found."))
    }
  }

  private def transformData(rawData: JsValue): JsResult[JsObject] = {
    val prependFieldsId = configuration.get[String]("service.customFieldId")
    val transformation = (__ \ "data").json.update(
      __.read[JsArray].map { feedData =>
        {
          val updatedFeedData = feedData.value.map { item =>
            val unixTimeStamp = DateTime.parse((item \ "timestamp").asOpt[String].getOrElse("")).getMillis / 1000
            item.as[JsObject] ++ JsObject(Map(
              s"${prependFieldsId}_api_version" -> JsString("v2"),
              s"${prependFieldsId}_created_time" -> JsString(unixTimeStamp.toString)))
          }

          JsArray(updatedFeedData)
        }
      })

    rawData.transform(transformation)
  }

  override def attachAccessToken(params: ApiEndpointCall, authInfo: OAuth2Info): ApiEndpointCall =
    params.copy(queryParameters = params.queryParameters + ("access_token" -> authInfo.accessToken))
}

object InstagramFeedInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.instagram.com",
    "/[userId]/media",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "250", "fields" -> "id,caption,media_type,media_url,permalink,thumbnail_url,timestamp,username"),
    Map(),
    Some(Map()))
}
