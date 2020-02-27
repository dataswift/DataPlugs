/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugInstagram.apiInterfaces

import java.time.LocalDateTime

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
import com.hubofallthings.dataplugInstagram.apiInterfaces.authProviders.InstagramProvider
import com.hubofallthings.dataplugInstagram.models.InstagramProfile
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class InstagramProfileInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: InstagramProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "instagram"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = InstagramProfileInterface.defaultApiEndpoint

  val refreshInterval = 7.days

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    None
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    params
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    val dataValidation =
      transformData(content)
        .map(validateMinDataStructure)
        .getOrElse(Failure(SourceDataProcessingException("Source data malformed, could not insert date in to the structure")))

    for {
      validatedData <- FutureTransformations.transform(dataValidation)
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  private def transformData(rawData: JsValue): JsResult[JsObject] = {

    val transformation = __.json.update(
      __.read[JsObject].map(profile => {

        profile ++ JsObject(Map(
          "hat_created_time" -> JsString(LocalDateTime.now().toString)))
      }))

    rawData.transform(transformation)
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    rawData.validate[InstagramProfile].isSuccess match {
      case true =>
        logger.info(s"Validated JSON Instagram profile object.")
        Success(JsArray(Seq(rawData)))
      case false =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }

  override def attachAccessToken(params: ApiEndpointCall, authInfo: OAuth2Info): ApiEndpointCall =
    params.copy(queryParameters = params.queryParameters + ("access_token" -> authInfo.accessToken))
}

object InstagramProfileInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.instagram.com",
    "/me",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("fields" -> "username,account_type,media_count"),
    Map(),
    Some(Map()))
}
