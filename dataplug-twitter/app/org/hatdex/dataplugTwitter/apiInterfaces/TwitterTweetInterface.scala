/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplugTwitter.apiInterfaces

import java.util.Locale

import akka.actor.ActorRef
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth1, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod, ApiEndpointTableStructure }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugTwitter.models._
import org.hatdex.hat.api.models.{ ApiDataRecord, ApiDataTable }
import org.joda.time.format.DateTimeFormat
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
    val provider: TwitterProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth1 {

  // JSON type formatters

  val sourceName: String = "twitter"
  val endpointName: String = "tweets"
  protected val logger: Logger = Logger("TwitterTweetsInterface")

  protected val apiEndpointTableStructures: Map[String, ApiEndpointTableStructure] = Map(
    "tweets" -> TwitterTweet
  )

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

    val rawTweets = content.as[JsArray] // Tweets returned by the API call

    // Shape results into HAT data records
    val resultsPosted = for {
      tweets <- FutureTransformations.transform(parseTweets(rawTweets)) // Parse tweets into strongly-typed structures
      tableStructures <- ensureDataTables(hatAddress, hatClientActor) // Ensure HAT data tables have been created
      apiDataRecords <- Future.sequence(tweets.map(convertTwitterTweetToHat(_, tableStructures)))
      posted <- uploadHatData(apiDataRecords, hatAddress, hatClientActor) // Upload the data
    } yield {
      debug(content, tweets)
      posted
    }

    resultsPosted
  }

  private def convertTwitterTweetToHat(tweet: TwitterTweet, tableStructures: Map[String, ApiDataTable]): Future[ApiDataRecord] = {
    val plainDataForRecords = buildJsonRecord(tweet)
    val format = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss Z yyyy").withLocale(Locale.ENGLISH)
    val recordTimestamp = Some(format.parseDateTime(tweet.created_at))
    buildHatDataRecord(plainDataForRecords, sourceName, tweet.id.toString, recordTimestamp, tableStructures)
  }

  private def debug(content: JsValue, tweets: Seq[TwitterTweet]): Unit = {
    // Tweets returned by the API call
    val tweets = content.as[JsArray]

    logger.debug(
      s"""Received following tweets:
          | - last updated $tweets""")
  }

  private def parseTweets(items: JsArray): Try[List[TwitterTweet]] = {
    items.validate[List[TwitterTweet]] match {
      case s: JsSuccess[List[TwitterTweet]] =>
        Success(s.get)
      case e: JsError =>
        val error = new RuntimeException(s"Error parsing event values: $e")
        logger.error(s"Error parsing event values: $e - ${items.toString()}")
        Failure(error)
    }
  }

  private def buildJsonRecord(tweet: TwitterTweet): JsArray = {
    val tweetJsonRecord = JsArray(List(JsObject(Map("tweets" -> Json.toJson(tweet)))))

    logger.debug(s"Tweets: ${Json.prettyPrint(tweetJsonRecord)}")

    tweetJsonRecord
  }

}

object TwitterTweetInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.twitter.com",
    "/1.1/statuses/user_timeline.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "200"),
    Map())
}
