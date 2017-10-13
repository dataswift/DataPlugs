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

class TwitterFriendInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: TwitterProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth1 {

  val namespace: String = "twitter"
  val endpoint: String = "friends"
  protected val logger: Logger = Logger("TwitterFriendsInterface")

  val defaultApiEndpoint = TwitterFriendInterface.defaultApiEndpoint

  val refreshInterval = 24.hours

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    val maybeUsers = (content \ "users").asOpt[JsArray]
    val maybeNextCursor = (content \ "next_cursor_str").asOpt[String]

    params.pathParameters.get("userId").map { userId =>
      val listContainsUser = maybeUsers map { users =>
        users.value.exists(v => (v \ "id_str").asOpt[String].contains(userId))
      } getOrElse {
        false
      }

      val userFirstInList = maybeUsers map { users =>
        users.value.headOption
          .map(firstUser => (firstUser \ "id_str").asOpt[String].contains(userId))
          .isDefined
      } getOrElse {
        false
      }

      val result: Option[ApiEndpointCall] = (listContainsUser, userFirstInList, maybeNextCursor) match {
        // Stop continuation, nothing to update
        case (true, true, _)       => None
        // Update HAT with new users and reset to the latest user ID
        case (true, false, _)      => Some(updatedParamsWithFirstUserId(maybeUsers, params))
        // Stop continuation, last page reached
        case (false, _, Some("0")) => None
        // Build continuation for the next page
        case (false, _, _) =>
          maybeNextCursor map { nextCursor =>
            val update = params.queryParameters + ("cursor" -> nextCursor)
            params.copy(queryParameters = update)
          }
      }

      result
    } getOrElse {
      Some(updatedParamsWithFirstUserId(maybeUsers, params))
    }
  }

  private def updatedParamsWithFirstUserId(userArray: Option[JsArray], params: ApiEndpointCall): ApiEndpointCall = {
    val maybeFirstUser = userArray flatMap { users =>
      users.value.headOption.flatMap { user =>
        (user \ "id_str").asOpt[String]
      }
    }

    maybeFirstUser map { firstUser =>
      val update = params.pathParameters + ("userId" -> firstUser)
      params.copy(pathParameters = update)
    } getOrElse {
      params
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = params

  override protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] =
    super[RequestAuthenticatorOAuth1].buildRequest(params)

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {

    for {
      users <- FutureTransformations.transform(validateMinDataStructure(content)) // Parse tweets into strongly-typed structures
      _ <- uploadHatData(namespace, endpoint, users, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "users").toOption.map {
      case data: JsArray if data.validate[List[TwitterUser]].isSuccess =>
        logger.debug(s"Validated JSON object:\n${data.toString}")
        Success(data)
      case data: JsArray =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }

}

object TwitterFriendInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.twitter.com",
    "/1.1/friends/list.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "200"),
    Map())
}
