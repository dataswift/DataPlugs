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
import com.hubofallthings.dataplugFitbit.models.FitbitWeight
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.joda.time.DateTime
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FitbitWeightInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FitbitProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "weight"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint: ApiEndpointCall = FitbitWeightInterface.defaultApiEndpoint
  val defaultApiDateFormat = FitbitWeightInterface.defaultApiDateFormat

  val cutoffDate = DateTime.parse("2017-01-01", defaultApiDateFormat)

  val refreshInterval: FiniteDuration = 24.hours

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    (
      params.pathParameters.get("baseDate"),
      params.pathParameters.get("endDate"),
      params.storage.get("earliestSyncedDate")) match {
        case (Some(baseDateStr), Some(endDateStr), Some(earliestSyncedDateStr)) =>
          val baseDate = DateTime.parse(baseDateStr, defaultApiDateFormat)
          val earliestSyncedDate = DateTime.parse(earliestSyncedDateStr, defaultApiDateFormat)

          if (baseDateStr == endDateStr && earliestSyncedDate.isAfter(cutoffDate)) {
            Some(params.copy(
              pathParameters = params.pathParameters +
                ("baseDate" -> earliestSyncedDate.minusDays(31).toString(defaultApiDateFormat),
                  "endDate" -> earliestSyncedDate.minusDays(1).toString(defaultApiDateFormat))))
          }
          else if (earliestSyncedDate.isAfter(cutoffDate)) {
            Some(params.copy(
              pathParameters = params.pathParameters +
                ("baseDate" -> baseDate.minusDays(31).toString(defaultApiDateFormat),
                  "endDate" -> baseDate.minusDays(1).toString(defaultApiDateFormat)),
              storageParameters = Some(params.storage + ("earliestSyncedDate" -> baseDateStr))))
          }
          else {
            None
          }
        case (_, _, _) => None
      }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    params.copy(pathParameters = params.pathParameters +
      ("baseDate" -> DateTime.now.toString(defaultApiDateFormat),
        "endDate" -> DateTime.now.toString(defaultApiDateFormat)))
  }

  override def buildFetchParameters(params: Option[ApiEndpointCall]): Future[ApiEndpointCall] = {
    logger.debug(s"Custom building fetch params: \n $params")

    val finalFetchParams = params.map { p =>
      p.pathParameters.get("baseDate").map { _ => p }.getOrElse {
        val updatedPathParams = p.pathParameters +
          ("baseDate" -> DateTime.now.minusDays(31).toString(defaultApiDateFormat),
            "endDate" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))
        val updatedStorageParams = p.storage + ("earliestSyncedDate" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))

        p.copy(pathParameters = updatedPathParams, storageParameters = Some(updatedStorageParams))
      }
    }.getOrElse {
      val updatedPathParams = defaultApiEndpoint.pathParameters +
        ("baseDate" -> DateTime.now.minusDays(31).toString(defaultApiDateFormat),
          "endDate" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))
      val updatedStorageParams = defaultApiEndpoint.storage + ("earliestSyncedDate" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))
      defaultApiEndpoint.copy(pathParameters = updatedPathParams, storageParameters = Some(updatedStorageParams))
    }

    logger.debug(s"Final fetch parameters: \n $finalFetchParams")

    Future.successful(finalFetchParams)
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "weight").toOption.map {
      case data: JsArray if data.validate[List[FitbitWeight]].isSuccess =>
        logger.info(s"Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsObject =>
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

object FitbitWeightInterface {
  val defaultApiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com",
    "/1/user/-/body/log/weight/date/[baseDate]/[endDate].json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))
}

