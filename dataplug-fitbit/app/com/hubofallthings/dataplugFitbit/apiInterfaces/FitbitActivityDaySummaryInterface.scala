/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 8, 2017
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
import com.hubofallthings.dataplugFitbit.models.FitbitActivitySummary
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.joda.time.{ DateTime, Days }
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FitbitActivityDaySummaryInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FitbitProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "activity/day/summary"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = FitbitActivityDaySummaryInterface.defaultApiEndpoint
  val defaultApiDateFormat = FitbitActivityDaySummaryInterface.apiDateFormat

  val refreshInterval = 24.hours

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    val dateParam = params.pathParameters.getOrElse("date", "")
    val syncDate = DateTime.parse(dateParam, defaultApiDateFormat)
    val maybeEarliestDateSynced = params.storage.get("earliestDateSynced")

    maybeEarliestDateSynced.map { earliestDateSynced =>
      val earliestDate = DateTime.parse(earliestDateSynced, defaultApiDateFormat)

      logger.warn(s"Days between: ${Days.daysBetween(earliestDate, DateTime.now).getDays}")

      if (Days.daysBetween(earliestDate, DateTime.now).getDays > 10) {
        None
      }
      else {
        Some(params.copy(
          storageParameters = Some(params.storage + ("earliestDateSynced" -> dateParam)),
          pathParameters = params.pathParameters + ("date" -> syncDate.minusDays(1).toString(defaultApiDateFormat))))
      }
    }.getOrElse {
      Some(params.copy(
        storageParameters = Some(params.storage + ("earliestDateSynced" -> dateParam)),
        pathParameters = params.pathParameters + ("date" -> syncDate.minusDays(1).toString(defaultApiDateFormat))))
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    val nextSyncDate = DateTime.now.toString(defaultApiDateFormat)
    val nextPathParameters = params.copy(pathParameters = params.pathParameters + ("date" -> nextSyncDate))

    nextPathParameters
  }

  override def buildFetchParameters(params: Option[ApiEndpointCall]): Future[ApiEndpointCall] = {
    logger.debug(s"Custom building fetch params: \n $params")

    val finalFetchParams = params.map { p =>
      p.pathParameters.get("date").map { _ => p }.getOrElse {
        val updatedParameters = p.pathParameters + ("date" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))
        p.copy(pathParameters = updatedParameters)
      }
    }.getOrElse {
      val updatedParameters = defaultApiEndpoint.pathParameters + ("date" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))

      defaultApiEndpoint.copy(pathParameters = updatedParameters)
    }

    logger.debug(s"Final fetch parameters: \n $finalFetchParams")

    Future.successful(finalFetchParams)
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    val dataValidation =
      transformData(content, fetchParameters.pathParameters("date"))
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

  private def transformData(rawData: JsValue, date: String): JsResult[JsObject] = {
    import play.api.libs.json._

    val transformation = (__ \ "summary").json.update(
      __.read[JsObject].map(o => o ++ JsObject(Map(
        "dateCreated" -> JsString(
          defaultApiDateFormat.withZoneUTC().parseDateTime(date).toString),
        "summaryDate" -> JsString(date)))))

    rawData.transform(transformation)
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "summary").toOption.map {
      case data: JsObject if data.validate[FitbitActivitySummary].isSuccess =>
        logger.info(s"Validated JSON day summary object.")
        val today = DateTime.now.toString(defaultApiDateFormat)
        val recordDate = (data \ "summaryDate").as[String]

        if (recordDate == today) {
          logger.debug(s"Record date is the same as today's date. Skipping.")
          Success(JsArray(Seq()))
        }
        else {
          logger.debug(s"Record date ($recordDate) does not match today's date ($today). Updating.")
          Success(JsArray(Seq(data)))
        }
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

object FitbitActivityDaySummaryInterface {
  val apiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com",
    "/1/user/-/activities/date/[date].json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))
}

