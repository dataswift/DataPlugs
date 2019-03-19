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

class TwitterFriendInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: TwitterProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth1 {

  val namespace: String = "twitter"
  val endpoint: String = "friends"
  protected val logger: Logger = Logger(this.getClass)
  val defaultApiEndpoint = TwitterFriendInterface.defaultApiEndpoint
  val refreshInterval = 1.minute

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    (content \ "next_cursor_str").as[String] match {
      case "0" => None

      case cursor: String => {
        logger.debug("Cursor found")
        val tempParams = checkIfWeHaveNewFollowers(content, checkForMostRecentFollower(content, params))
        tempParams match {
          case Some(parameters) =>
            val maybeMostRecentFollower = params.storageParameters.flatMap(_.get("mostRecentFollower"))
            maybeMostRecentFollower match {
              case Some(_) => Some(parameters.copy(queryParameters = parameters.queryParameters + ("cursor" -> "-1")))
              case _       => Some(parameters.copy(queryParameters = parameters.queryParameters + ("cursor" -> cursor)))
            }

          case _ =>
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

      case _ => params
    }
  }

  private def checkIfWeHaveNewFollowers(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    val maybeTempMostRecentFollower = params.storageParameters.flatMap(_.get("tempMostRecentFollower"))
    val maybeMostRecentFollower = params.storageParameters.flatMap(_.get("mostRecentFollower"))

    logger.debug(s"maybeTempMostRecentFollower $maybeTempMostRecentFollower")
    logger.debug(s"maybeMostRecentFollower $maybeMostRecentFollower")

    maybeMostRecentFollower match {
      case Some(value) => // This is NOT the first sync ever

        maybeTempMostRecentFollower match {
          case Some(_) => //means this is not the first sync EVER and we have gone through the new data
            logger.debug("means this is not the first sync EVER and we have gone through the new data")
            val users = (content \ "users").as[JsArray].validate[Seq[TwitterUser]].get
            users.find { user => user.id.toString == value } match {
              case Some(_) =>
                logger.debug("we found the id we have saved. No need to sync the rest data")
                None // we found the id we have saved. No need to sync the rest data

              case _ =>
                logger.debug("Possibly the user we had saved has been removed from followers")
                Some(params) // Possibly the user we had saved has been removed from followers
            }

          case _ =>
            logger.debug("This should not happen really. Means this is NOT the first sync and we have NOT gone through the new data, makes no sense")
            Some(params) // This should not happen really. Means this is NOT the first sync and we have NOT gone through the new data, makes no sense
        }

      //This is the first sync ever
      case _ => Some(params)
    }
  }

  private def updateStorageParametersMostRecentFollower(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Build next sync parameters are: ${params}")
    val maybeTempMostRecentFollower = params.storageParameters.flatMap(_.get("tempMostRecentFollower"))

    maybeTempMostRecentFollower match {
      case Some(value) =>
        logger.debug(s"This is the first sync. The most recent follower is:  ${value}")
        params.copy(storageParameters = Some(params.storage - "mostRecentFollower" - "tempMostRecentFollower" ++ Map("mostRecentFollower" -> value)))

      case _ =>
        logger.debug("No recent follower has been found")
        val maybeMostRecentUser = (content \ "users").as[JsArray].validate[Seq[TwitterUser]].get.headOption
        maybeMostRecentUser match {
          case Some(value) => params.copy(storageParameters = Some(params.storage - "mostRecentFollower" - "tempMostRecentFollower" ++ Map("mostRecentFollower" -> value.id.toString)))

          case _           => params
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
      clippedData <- transformData(content, fetchParameters)
      users <- FutureTransformations.transform(validateMinDataStructure(clippedData)) // Parse tweets into strongly-typed structures
      _ <- uploadHatData(namespace, endpoint, users, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  private def transformData(content: JsValue, params: ApiEndpointCall): Future[JsValue] = {
    val maybeMostRecentFollower = params.storageParameters.flatMap(_.get("mostRecentFollower"))
    maybeMostRecentFollower match {
      case Some(recentFollower) =>
        val maybeUsers = (content \ "users").validate[Seq[TwitterUser]]
        maybeUsers match {
          case JsSuccess(value, _) => Future.successful(Json.toJson(value.takeWhile { user => user.id.toString != recentFollower }))

          case JsError(_)          => Future.successful((content \ "users").asOpt[JsValue].getOrElse(content))
        }

      case _ => Future.successful((content \ "users").asOpt[JsValue].getOrElse(content))
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    rawData match {
      case data: JsArray if data.validate[List[TwitterUser]].isSuccess =>
        logger.debug(s"Validated JSON object: \n${data.value.length}")
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

object TwitterFriendInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.twitter.com",
    "/1.1/friends/list.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "200"),
    Map(),
    Some(Map()))
}
