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
import akka.util.Timeout
import com.google.inject.Inject
import com.hubofallthings.dataplug.actors.Errors.SourceDataProcessingException
import com.hubofallthings.dataplug.apiInterfaces.DataPlugEndpointInterface
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import com.hubofallthings.dataplugInstagram.models.InstagramMedia
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import com.mohiva.play.silhouette.impl.providers.oauth2.InstagramProvider
import play.api.Logger
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
    val provider: InstagramProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "instagram"
  val endpoint: String = "feed"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = InstagramFeedInterface.defaultApiEndpoint

  val refreshInterval = 1.hour

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val maybeMinIdParam = params.pathParameters.get("min_id")
    if (maybeMinIdParam.isDefined) {
      None
    }
    else {
      val maybeNextMaxId = (content \ "pagination" \ "next_max_id").asOpt[String]
      val maybeMinIdStorage = params.storage.get("min_id")

      (maybeNextMaxId, maybeMinIdStorage) match {
        case (Some(nextMaxId), Some(_)) =>
          Some(params.copy(queryParameters = params.queryParameters + ("max_id" -> nextMaxId)))
        case (Some(nextMaxId), None) =>
          Some(params.copy(
            queryParameters = params.queryParameters + ("max_id" -> nextMaxId),
            storageParameters = Some(params.storage + ("min_id" -> extractHeadId(content).get))))
        case (None, _) => None
      }
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    params.storage.get("min_id").map { savedMinId =>
      params.copy(
        queryParameters = params.queryParameters + ("min_id" -> savedMinId) - "max_id",
        storageParameters = Some(params.storage - "min_id"))
    }.getOrElse {
      val maybeHeadId = extractHeadId(content)

      maybeHeadId.map { headId =>
        params.copy(queryParameters = params.queryParameters + ("min_id" -> headId))
      }.getOrElse(params)
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content))
      processedData <- transformData(validatedData, fetchParameters)
      _ <- uploadHatData(namespace, endpoint, processedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully uploaded ${processedData.value.length} new records to $hatAddress HAT")
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

  private def transformData(value: JsArray, params: ApiEndpointCall): Future[JsArray] = {
    val postsWithSortedArrays = value.value.map { post =>
      Try {
        val sortedTags = (post \ "tags").as[Seq[String]].sorted
        post.as[JsObject] ++ Json.obj("tags" -> JsArray(sortedTags.map(JsString)))
      }.getOrElse(post)
    }

    Future.successful(JsArray(postsWithSortedArrays))
  }

  private def extractHeadId(value: JsValue): Try[String] = Try((value \ "data" \ 0 \ "id").as[String])

  override def attachAccessToken(params: ApiEndpointCall, authInfo: OAuth2Info): ApiEndpointCall =
    params.copy(queryParameters = params.queryParameters + ("access_token" -> authInfo.accessToken))
}

object InstagramFeedInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.instagram.com/v1",
    "/users/self/media/recent",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "100"),
    Map(),
    Some(Map()))
}
