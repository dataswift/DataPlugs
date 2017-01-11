/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplugTwitter.apiInterfaces

import akka.actor.ActorRef
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticatorOAuth1
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod, ApiEndpointTableStructure }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugTwitter.models._
import org.hatdex.hat.api.models.{ ApiDataRecord, ApiDataTable }
import org.joda.time.DateTime
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class TwitterFollowerInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val provider: TwitterProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth1 {

  // JSON type formatters

  val sourceName: String = "twitter"
  val endpointName: String = "followers"
  protected val logger: Logger = Logger("TwitterFollowersInterface")

  protected val apiEndpointTableStructures: Map[String, ApiEndpointTableStructure] = Map(
    "followers" -> TwitterUser
  )

  val defaultApiEndpoint = TwitterFollowerInterface.defaultApiEndpoint

  val refreshInterval = 5.minutes

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    (content \ "next_cursor_str").as[String] match {
      case "0" => None
      case cursor: String => {
        Some(params.copy(queryParameters = params.queryParameters + ("cursor" -> cursor)))
      }
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    //implement
    //    val maybeNextSyncToken = (content \ "nextSyncToken").asOpt[String]
    //
    //    maybeNextSyncToken map { nextSyncToken =>
    //      val updatedParams = params.queryParameters + ("syncToken" -> nextSyncToken) - "pageToken"
    //      params.copy(queryParameters = updatedParams)
    //    } getOrElse {
    //      params
    //    }
    params
  }

  override protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] =
    super[RequestAuthenticatorOAuth1].buildRequest(params)

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {

    val rawFollowers = (content \ "users").as[JsArray] // Tweets returned by the API call

    // Shape results into HAT data records
    val resultsPosted = for {
      users <- FutureTransformations.transform(parseUsers(rawFollowers)) // Parse tweets into strongly-typed structures
      tableStructures <- ensureDataTables(hatAddress, hatClientActor) // Ensure HAT data tables have been created
      apiDataRecords <- Future.sequence(users.map(convertTwitterUserToHat(_, tableStructures)))
      posted <- uploadHatData(apiDataRecords, hatAddress, hatClientActor) // Upload the data
    } yield {
      debug(content, users)
      posted
    }

    resultsPosted
  }

  private def convertTwitterUserToHat(user: TwitterUser, tableStructures: Map[String, ApiDataTable]): Future[ApiDataRecord] = {
    val plainDataForRecords = buildJsonRecord(user)
    val recordTimestamp = Some(new DateTime(user.created_at))
    buildHatDataRecord(plainDataForRecords, sourceName, user.id.toString, recordTimestamp, tableStructures)
  }

  private def debug(content: JsValue, tweets: Seq[TwitterUser]): Unit = {
    // Calendar information returned by the API call
  }

  private def parseUsers(items: JsArray): Try[List[TwitterUser]] = {
    items.validate[List[TwitterUser]] match {
      case s: JsSuccess[List[TwitterUser]] =>
        Success(s.get)
      case e: JsError =>
        val error = new RuntimeException(s"Error parsing event values: $e")
        logger.error(s"Error parsing event values: $e - ${items.toString()}")
        Failure(error)
    }
  }

  private def buildJsonRecord(user: TwitterUser): JsArray = {
    val userJsonRecord = JsArray(List(JsObject(Map("friends" -> Json.toJson(user)))))

    logger.debug(s"Followers: ${Json.prettyPrint(userJsonRecord)}")

    userJsonRecord
  }

}

object TwitterFollowerInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.twitter.com",
    "/1.1/followers/list.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "200"),
    Map())
}
