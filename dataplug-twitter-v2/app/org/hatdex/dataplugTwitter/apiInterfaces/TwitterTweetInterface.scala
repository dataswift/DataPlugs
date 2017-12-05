/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugTwitter.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticatorOAuth1
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugTwitter.models._
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class TwitterTweetInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: TwitterProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth1 {

  // JSON type formatters

  val namespace: String = "twitter"
  val endpoint: String = "tweets"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = TwitterTweetInterface.defaultApiEndpoint

  val refreshInterval = 60.minutes

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    val maybeTweets = content.asOpt[JsArray]

    // If it's a first continuation call, the ID of the first tweet will be saved into path parameters
    val continuationParams = params.pathParameters.get("firstTweetId").map { _ =>
      params
    } getOrElse {
      val maybeFirstTweetId = maybeTweets flatMap { tweets =>
        tweets.value.headOption.flatMap { tweet =>
          (tweet \ "id_str").asOpt[String]
        }
      }

      maybeFirstTweetId map { firstTweetId =>
        val updateParams = params.pathParameters + ("firstTweetId" -> firstTweetId)
        params.copy(pathParameters = updateParams)
      } getOrElse {
        params
      }
    }

    val maybeLastTweetId = maybeTweets flatMap { tweets =>
      tweets.value.lastOption.flatMap { tweet =>
        (tweet \ "id").asOpt[Long]
      }
    }

    maybeLastTweetId map { lastTweetId =>
      val updateParams = continuationParams.queryParameters + ("max_id" -> (lastTweetId - 1).toString)
      Some(continuationParams.copy(queryParameters = updateParams))
    } getOrElse {
      None
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    // Path parameters Map is used to store the first tweet ID retrieved during initial call of the buildContinuation function
    params.pathParameters.get("firstTweetId").map { firstTweetId =>
      val updatedQueryParams = params.queryParameters + ("since_id" -> firstTweetId) - "max_id"
      val updatedPathParams = params.pathParameters - "firstTweetId"
      params.copy(pathParameters = updatedPathParams, queryParameters = updatedQueryParams)
    } getOrElse {
      val updatedQueryParams = params.queryParameters - "max_id"
      params.copy(queryParameters = updatedQueryParams)
    }
  }

  override protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] =
    super[RequestAuthenticatorOAuth1].buildRequest(params)

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {

    for {
      tweets <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, tweets, hatAddress, hatClientActor)
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    rawData match {
      case data: JsArray if data.validate[List[TwitterTweet]].isSuccess =>
        logger.info(s"Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsArray =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }

}

object TwitterTweetInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.twitter.com",
    "/1.1/statuses/user_timeline.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "200"),
    Map(),
    Some(Map()))
}
