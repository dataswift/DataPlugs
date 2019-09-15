/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 3, 2019
 */

package com.hubofallthings.dataplugFitbit.apiInterfaces

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
import com.hubofallthings.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import com.hubofallthings.dataplugFitbit.models.FitbitWeightGoal
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.joda.time.DateTime
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject, JsValue }
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FitbitWeightGoalsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FitbitProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "goals/weight"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = FitbitWeightGoalsInterface.defaultApiEndpoint

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

    val transformedData = transformData(content).getOrElse(JsObject(Map.empty[String, JsValue]))

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(transformedData))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  private def transformData(rawData: JsValue): Option[JsObject] = {
    import play.api.libs.json._

    val transformation = (__ \ "goal").json.update(
      __.read[JsObject].map(o => o ++ JsObject(Map("hatUpdatedTime" -> JsString(DateTime.now.toString)))))

    (rawData \ "goal").asOpt[JsObject] match {
      case Some(value) if value.values.nonEmpty => value.transform(transformation).asOpt
      case _                                    => None
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "goal").toOption.map {
      case data: JsValue if data.validate[FitbitWeightGoal].isSuccess =>
        logger.info(s"Validated JSON for fitbit weight goal.")
        Success(JsArray(Seq(data)))
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      rawData.asOpt[JsObject] match {
        case Some(value) if value.values.isEmpty =>
          logger.info(s"Error validating data, value was empty:\n${value.toString}")
          Success(JsArray(Seq()))

        case _ =>
          logger.error(s"Error parsing JSON object, necessary property not found: ${rawData.toString}")
          Failure(SourceDataProcessingException(s"Error parsing JSON object, necessary property not found."))
      }
    }
  }
}

object FitbitWeightGoalsInterface {
  val apiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com",
    "/1.2/user/-/body/log/weight/goal.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))
}
