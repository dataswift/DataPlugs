/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugTwitter.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticatorOAuth1
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplugTwitter.models._
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class TwitterFollowerInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: TwitterProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth1 {

  // JSON type formatters

  val namespace: String = "twitter"
  val endpoint: String = "followers"
  protected val logger: Logger = Logger(this.getClass)
  val defaultApiEndpoint = TwitterFollowerInterface.defaultApiEndpoint
  val refreshInterval = 24.hours

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    (content \ "next_cursor_str").as[String] match {
      case "0" => None

      case cursor: String => {
        logger.debug("Cursor found")
        val tempParams = checkForNewFollowers(content, checkForMostRecentFollower(content, params))
        tempParams match {
          case Some(parameters) =>
            val maybeMostRecentFollower = params.storageParameters.flatMap(_.get("mostRecentFollower"))
            maybeMostRecentFollower match {
              case Some(_) => Some(parameters.copy(queryParameters = parameters.queryParameters + ("cursor" -> "-1")))
              case _       => Some(parameters.copy(queryParameters = parameters.queryParameters + ("cursor" -> cursor)))
            }

          case None =>
            logger.debug(s"No new parameters")
            None
        }
      }
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    updateStorageParametersMostRecentFollower(content, params)
  }

  private def checkForMostRecentFollower(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    val maybeMostRecentFollower = params.storageParameters.flatMap(_.get("tempMostRecentFollower"))
    maybeMostRecentFollower match {
      case None =>
        (content \ "users").as[JsArray].value.head.validate[TwitterUser].map { user =>
          logger.debug(s"the most recent follower found in JSON is:  ${{ user.id.toString }}")
          params.copy(storageParameters = Some(params.storage ++ Map("tempMostRecentFollower" -> user.id.toString)))
        }.getOrElse(params)

      case Some(_) => params
    }
  }

  private def searchForUser(content: JsValue, mostRecentFollower: String, params: ApiEndpointCall) = {
    val users = (content \ "users").asOpt[Seq[TwitterUser]].getOrElse(Seq.empty[TwitterUser])
    val maybeUser = users.find { user => user.id.toString == mostRecentFollower }
    maybeUser match {
      case Some(_) =>
        logger.debug("we found the id we have saved. No need to sync the rest data")
        None

      case None =>
        logger.debug("Possibly the user we had saved has been removed from followers")
        Some(params)
    }
  }

  private def checkForNewFollowers(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    val maybeCurrentMostRecentFollower = params.storageParameters.flatMap(_.get("tempMostRecentFollower"))
    val maybeCachedMostRecentFollower = params.storageParameters.flatMap(_.get("mostRecentFollower"))

    if (maybeCachedMostRecentFollower.isDefined && maybeCurrentMostRecentFollower.isDefined) {
      searchForUser(content, maybeCachedMostRecentFollower.get, params)
    }
    else {
      if (maybeCachedMostRecentFollower.isDefined && maybeCurrentMostRecentFollower.isEmpty) {
        logger.warn("This should not happen really. Means this is NOT the first sync and we have NOT gone through the new data, makes no sense")
      }
      Some(params)
    }
  }

  private def updateStorageParametersMostRecentFollower(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    val result = for {
      mostRecentFollower <- params.storageParameters.flatMap(_.get("tempMostRecentFollower"))
    } yield {
      logger.debug(s"This is the first sync")
      params.copy(queryParameters = params.queryParameters - "cursor", storageParameters = Some(params.storage - "mostRecentFollower" - "tempMostRecentFollower" ++ Map("mostRecentFollower" -> mostRecentFollower)))
    }

    result.getOrElse {
      logger.debug("No recent follower has been found")
      val maybeMostRecentUser = (content \ "users").asOpt[Seq[TwitterUser]].getOrElse(Seq.empty[TwitterUser]).headOption
      maybeMostRecentUser match {
        case Some(value) => params.copy(queryParameters = params.queryParameters - "cursor", storageParameters = Some(params.storage - "mostRecentFollower" - "tempMostRecentFollower" ++ Map("mostRecentFollower" -> value.id.toString)))
        case None        => params.copy(queryParameters = params.queryParameters - "cursor")
      }
    }
  }

  override protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] =
    super[RequestAuthenticatorOAuth1].buildRequest(params)

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      users <- FutureTransformations.transform(validateMinDataStructure(content)) // Parse tweets into strongly-typed structures
      _ <- uploadHatData(namespace, endpoint, users, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "users").toOption.map {
      case data: JsArray if data.validate[List[TwitterUser]].isSuccess =>
        logger.debug(s"Validated JSON object: \n${data.value.length}")
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

object TwitterFollowerInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.twitter.com",
    "/1.1/followers/list.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "200"),
    Map(),
    Some(Map()))
}
