/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 11 2019
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
import com.hubofallthings.dataplugFacebook.models.FacebookPost
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.hubofallthings.dataplugFacebook.apiInterfaces.authProviders._
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject, JsValue }
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FacebookPostsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FacebookProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "facebook"
  val endpoint: String = "posts"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = FacebookPostsInterface.defaultApiEndpoint

  val refreshInterval = 1.hour

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    logger.debug(s"Content is $content")

    val maybeData = (content \ "data").toOption.exists {
      case data: JsArray => data.value.isEmpty
      case _             => false
    }
    val maybeNextPage = (content \ "paging" \ "next").asOpt[String]
    val maybeSinceParam = params.pathParameters.get("since")

    logger.debug(s"Found possible next page link: $maybeNextPage")
    logger.debug(s"Found possible next since parameter: $maybeSinceParam")

    if (!maybeData) {
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
    else {
      logger.debug(s"No Data: $maybeData")
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
      case data: JsArray if data.validate[List[FacebookPost]].isSuccess =>
        logger.info(s"[$hatAddress] Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsArray =>
        logger.warn(s"[$hatAddress] Could not validate full item list. Parsing ${data.value.length} data items one by one.")
        Success(JsArray(data.value.filter(_.validate[FacebookPost].isSuccess)))
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

object FacebookPostsInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v9.0",
    "/me/posts",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "100", "fields" -> ("id,caption,created_time,description,from,full_picture,icon,link,is_instagram_eligible," +
      "message,message_tags,name,object_id,permalink_url,place,shares,status_type,type,updated_time,with_tags")),
    Map(),
    Some(Map()))
}
