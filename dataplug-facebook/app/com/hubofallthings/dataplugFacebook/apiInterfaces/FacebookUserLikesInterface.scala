/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 3, 2019
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
import com.hubofallthings.dataplugFacebook.models.{ FacebookPost, FacebookUserLikes }
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.hubofallthings.dataplugFacebook.apiInterfaces.authProviders._
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class FacebookUserLikesInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FacebookProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val defaultApiEndpoint = FacebookUserLikesInterface.defaultApiEndpoint

  val namespace: String = "facebook"
  val endpoint: String = "likes/pages"
  protected val logger: Logger = Logger(this.getClass)

  val refreshInterval = 1.day // No idea if this is ideal, might do with longer?

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val maybeData = (content \ "data").toOption.exists {
      case data: JsArray => data.value.isEmpty
      case _             => false
    }
    val maybeNextPage = (content \ "paging" \ "next").asOpt[String]
    val maybeBeforeParam = params.pathParameters.get("before")

    if (!maybeData) {
      maybeNextPage.map { nextPage =>
        logger.debug(s"Found next page link (continuing sync): $nextPage")

        val nextPageUri = Uri(nextPage)
        val updatedQueryParams = params.queryParameters ++ nextPageUri.query().toMap

        logger.debug(s"Updated query parameters: $updatedQueryParams")

        if (maybeBeforeParam.isDefined) {
          logger.debug("\"Before\" parameter already set, updating query params")
          params.copy(queryParameters = updatedQueryParams)
        }
        else {
          (content \ "paging" \ "cursors" \ "before").asOpt[String].map { beforeParameter =>
            val updatedPathParams = params.pathParameters + ("before" -> beforeParameter)

            logger.debug(s"Updating query params and setting 'before': $beforeParameter")
            params.copy(pathParameters = updatedPathParams, queryParameters = updatedQueryParams)
          }
        }.getOrElse {
          logger.warn("Unexpected API behaviour: 'before' not set and it was not possible to extract it from response body")
          params.copy(queryParameters = updatedQueryParams)
        }
      }
    }
    else {
      None
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    val updatedQueryParams = params.queryParameters - "__paging_token" - "access_token" - "__previous"

    logger.debug(s"Updated query parameters: $updatedQueryParams")

    val sinceParameter = DateTime.now().getMillis / 1000
    val untilParameter = DateTime.now().plusDays(2).getMillis / 1000
    params.copy(
      pathParameters = params.pathParameters - "since",
      queryParameters = updatedQueryParams + ("since" -> sinceParameter.toString) + ("until" -> untilParameter.toString))
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    val dataValidation =
      transformData(content)
        .map(validateMinDataStructure(_, hatAddress))
        .getOrElse(Failure(SourceDataProcessingException(s"[$hatAddress] Source data malformed, could not insert date in to the structure")))

    for {
      validatedData <- FutureTransformations.transform(dataValidation)
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  private def transformData(rawData: JsValue): JsResult[JsObject] = {

    val totalPagesLiked = (rawData \ "summary" \ "total_count").asOpt[JsNumber].getOrElse(JsNumber(0))
    val transformation = (__ \ "data").json.update(
      __.read[JsArray].map(pagesLikesData => {
        val updatedLikesData = pagesLikesData.value.map { like =>
          like.as[JsObject] ++ JsObject(Map("number_of_pages_liked" -> totalPagesLiked))
        }

        JsArray(updatedLikesData)
      }))

    rawData.transform(transformation)
  }

  override def validateMinDataStructure(rawData: JsValue, hatAddress: String): Try[JsArray] = {

    (rawData \ "data").toOption.map {
      case data: JsArray if data.validate[List[FacebookUserLikes]].isSuccess =>
        logger.info(s"[$hatAddress] Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsArray =>
        logger.warn(s"[$hatAddress] Could not validate full item list. Parsing ${data.value.length} data items one by one.")
        Success(JsArray(data.value.filter(_.validate[FacebookUserLikes].isSuccess)))
      case data: JsObject =>
        logger.error(s"[$hatAddress] Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"[$hatAddress] Error parsing JSON object: ${data.validate[List[FacebookPost]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"[$hatAddress] Error parsing JSON object, necessary property not found: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object, necessary property not found."))
    }
  }
}

object FacebookUserLikesInterface {
  val baseApiUrl = ConfigFactory.load.getString("service.baseApiUrl")
  val defaultApiEndpoint = ApiEndpointCall(
    baseApiUrl,
    "/me/likes",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("summary" -> "total_count", "limit" -> "500", "fields" -> ("id,about,created_time,awards,can_checkin,can_post,category,category_list,checkins," +
      "description,description_html,display_subtext,emails,fan_count,has_whatsapp_number,link,location,name,overall_star_rating," +
      "place_type,rating_count,username,verification_status,website,whatsapp_number")),
    Map(),
    Some(Map()))
}
