/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 4, 2019
 */

package com.hubofallthings.dataplugSpotify.apiInterfaces

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
import com.hubofallthings.dataplugSpotify.apiInterfaces.authProviders.SpotifyProvider
import com.hubofallthings.dataplugSpotify.models.SpotifyUsersPlaylist
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class SpotifyUserPlaylistsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: SpotifyProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "spotify"
  val endpoint: String = "playlists/user"
  protected val logger: Logger = Logger(this.getClass)
  val defaultApiEndpoint: ApiEndpointCall = SpotifyUserPlaylistsInterface.defaultApiEndpoint
  val refreshInterval: FiniteDuration = 1.day

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val nextQueryParams = for {
      nextLink <- Try((content \ "next").asOpt[String].getOrElse("")) if nextLink.nonEmpty
      queryParams <- Try(Uri(nextLink).query().toMap) if queryParams.size == 2
    } yield queryParams

    (nextQueryParams, params.storage.get("offset")) match {
      case (Success(qp), Some(_)) =>
        logger.debug(s"Next continuation params: $qp")
        Some(params.copy(queryParameters = params.queryParameters ++ qp))

      case (Success(qp), None) =>
        val offsetParameter = (content \ "offset").as[Int].toString
        logger.debug(s"Next continuation params: $qp, setting offset to $offsetParameter")
        Some(params.copy(queryParameters = params.queryParameters ++ qp, storageParameters = Some(params.storage +
          ("offset" -> offsetParameter))))

      case (Failure(e), _) =>
        logger.debug(s"Next link NOT found. Terminating continuation. $e")
        None
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    params.storage.get("offset").map { offSet => // After date set (there was at least one continuation step)
      Try((content \ "items").as[JsArray].value) match {
        case Success(_) => // Did continuation but there was no `nextPage` found, if track array present it's a successful completion
          val nextStorage = params.storage - "offset"
          val nextQuery = params.queryParameters - "before" + ("offset" -> offSet)
          params.copy(queryParameters = nextQuery, storageParameters = Some(nextStorage))

        case Failure(e) => // Cannot extract tracks array value, most likely an error was returned by the API, continue from where finished previously
          logger.error(s"Provider API request error while performing continuation: $content.The error is: $e")
          params
      }
    }.getOrElse { // After date not set, no continuation steps took place
      val offsetParameter = (content \ "offset").as[Int].toString
      val nextQuery = params.queryParameters - "before" + ("offset" -> offsetParameter)
      params.copy(queryParameters = nextQuery)
    }
  }

  private def generateChangedPlaylistIds(content: JsValue, fetchParameters: ApiEndpointCall): Map[String, String] = {
    val savedSnapshotIdStorage = fetchParameters.storage

    logger.debug(s"The content is: $content")
    (content \ "items").asOpt[Seq[SpotifyUsersPlaylist]].map { playlists =>
      playlists.foldLeft(Map.empty[String, String]) { (accumulator, value) =>

        logger.debug(s"The playlist is: $value")
        val maybeSnapshotId = savedSnapshotIdStorage.find(_._1 == value.id)
        logger.debug(s"The found snaphostID is: $maybeSnapshotId")
        maybeSnapshotId match {
          case Some(snapshot) if (snapshot._2 != value.snapshot_id) =>
            accumulator ++ Map(snapshot)

          case None => accumulator ++ Map(value.id -> value.snapshot_id)
        }
      }
    }.getOrElse(Map.empty[String, String])
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    val dataValidation = validateMinDataStructure(rawData = content)

    for {
      result <- Future.successful(generateChangedPlaylistIds(content, fetchParameters))
      validatedData <- FutureTransformations.transform(dataValidation)
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    logger.error(s"Body: $rawData")
    rawData match {
      case data: JsObject if (data \ "items").validate[Seq[SpotifyUsersPlaylist]].isSuccess =>
        logger.info(s"Validated JSON spotify user playlists object.")
        Success((data \ "items").as[JsArray])
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }
}

object SpotifyUserPlaylistsInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.spotify.com",
    "/v1/me/playlists",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "50"),
    Map(),
    Some(Map()))
}

