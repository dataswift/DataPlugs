/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 5, 2017
 */

package com.hubofallthings.dataplugFacebook.apiInterfaces

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
import com.hubofallthings.dataplugFacebook.models.FacebookProfilePicture
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.hubofallthings.dataplugFacebook.apiInterfaces.authProviders._
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FacebookProfilePictureInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FacebookProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "facebook"
  val endpoint: String = "profile/picture"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = FacebookProfileInterface.defaultApiEndpoint

  val refreshInterval = 24.hours

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
      case data: JsObject if data.validate[FacebookProfilePicture].isSuccess =>
        logger.info(s"[$hatAddress] Validated JSON facebook profile photo object.")
        Success(JsArray(Seq(data)))
      case data: JsObject =>
        logger.error(s"[$hatAddress] Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"[$hatAddress] Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"[$hatAddress] Error parsing JSON object, necessary property not found: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object, necessary property not found."))
    }
  }
}

object FacebookProfilePictureInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v5.0",
    "/me/picture",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("height" -> "320", "width" -> "320", "redirect" -> "false"),
    Map(),
    Some(Map()))
}
