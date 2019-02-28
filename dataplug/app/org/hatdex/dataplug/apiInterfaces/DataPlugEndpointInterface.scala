/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import org.hatdex.dataplug.actors.Errors.{ DataPlugError, _ }
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticator
import org.hatdex.dataplug.apiInterfaces.models._
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureRetries }
import org.hatdex.hat.api.services.Errors.{ ApiException, DuplicateDataException }
import org.joda.time.DateTime
import play.api.http.Status._
import play.api.libs.json.{ JsArray, JsValue, Json }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait DataPlugEndpointInterface extends DataPlugApiEndpointClient with RequestAuthenticator {
  val refreshInterval: FiniteDuration
  protected implicit val scheduler: Scheduler

  /**
   * Fetch data from an API endpoint as per parametrised configuration, for a specific HAT client
   *
   * @param fetchParams API endpoint parameters generic (stateless) for the endpoint
   * @param hatAddress HAT Address (domain)
   * @param hatClient HAT client for the specific HAT
   * @return Potentially updated set of parameters, e.g. with new timestamps
   */
  def fetch(fetchParams: ApiEndpointCall, hatAddress: String, hatClient: AuthenticatedHatClient)(implicit ec: ExecutionContext, timeout: Timeout): Future[DataPlugFetchStep] = {
    processDataFetch(fetchParams, hatAddress, hatClient, retrying = false)
  }

  protected def processDataFetch(
    fetchParams: ApiEndpointCall,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    retrying: Boolean)(implicit ec: ExecutionContext, timeout: Timeout): Future[DataPlugFetchStep] = {

    val authenticatedFetchParameters = authenticateRequest(fetchParams, hatAddress, refreshToken = retrying)

    authenticatedFetchParameters flatMap { requestParameters =>
      logger.debug(s"The parameters are: $requestParameters")
      buildRequest(requestParameters)
    } flatMap { result =>
      logger.debug(s"fetch returned: ${result}")
      result.status match {
        case OK =>
          processResults(result.json, hatAddress, hatClient, fetchParams) map { _ =>
            logger.debug(s"Successfully processed request for $hatAddress to save data from ${fetchParams.url}${fetchParams.path}")
            buildContinuation(result.json, fetchParams)
              .map(DataPlugFetchContinuation)
              .getOrElse(DataPlugFetchNextSync(buildNextSync(result.json, fetchParams)))
          } recoverWith {
            case e: HATApiError =>
              if (logger.isDebugEnabled) {
                mailer.serverExceptionNotifyInternal(s"""
                   | Error when communicating data to HAT $hatAddress.
                   | Fetch Parameters: $fetchParams.
                   | Content: ${Json.prettyPrint(result.json)}
                  """.stripMargin, e)
              }
              Future.failed(e)

            case e: SourceApiError =>
              if (logger.isDebugEnabled) {
                mailer.serverExceptionNotifyInternal(s"""
                   | Error when retrieving data from source for $hatAddress.
                   | Fetch Parameters: $fetchParams.
                   | Content: ${Json.prettyPrint(result.json)}
                    """.stripMargin, e)
              }
              Future.failed(e)
          }

        case UNAUTHORIZED =>
          if (!retrying) {
            logger.debug(s"Unauthorized request $fetchParams for $hatAddress - ${result.status}: ${result.body}")
            processDataFetch(fetchParams, hatAddress, hatClient, retrying = true)
          }
          else {
            logger.warn(s"Unauthorized retried request $fetchParams for $hatAddress - ${result.status}: ${result.body}")
            Future.successful(DataPlugFetchNextSync(fetchParams))
          }
        case NOT_FOUND =>
          logger.warn(s"Not found for request $fetchParams - ${result.status}: ${result.body}")
          Future.failed(SourceApiCommunicationException(s"Not found for request $fetchParams for $hatAddress - ${result.status}: ${result.body}"))
        case _ =>
          logger.warn(s"Unsuccessful response from api endpoint $fetchParams for $hatAddress - ${result.status}: ${result.body}")
          Future.successful(DataPlugFetchNextSync(fetchParams))
      }
    } recoverWith {
      case e: DataPlugError =>
        val message = s"${e.getClass.getSimpleName} Error when querying api endpoint $fetchParams for $hatAddress - ${e.getMessage}"
        logger.warn(message)
        Future.failed(e)
      case e =>
        val message = s"${e.getClass.getSimpleName} Error when querying api endpoint $fetchParams for $hatAddress - ${e.getMessage}"
        logger.warn(message)
        Future.failed(new DataPlugError(message, e))
    }
  }

  protected def processResults(content: JsValue, hatAddress: String, hatClient: AuthenticatedHatClient, fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {
    val retries: List[FiniteDuration] = FutureRetries.withJitter(List(20.seconds, 2.minutes, 5.minutes, 10.minutes), 0.2, 0.5)
    FutureRetries.retry(uploadHatData(namespace, endpoint, content, hatAddress, hatClient), retries)
  }

  protected def uploadHatData(
    namespace: String,
    endpoint: String,
    data: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    val batchdata: JsArray = data match {
      case v: JsArray => v
      case v: JsValue => JsArray(Seq(v))
    }

    if (batchdata.value.nonEmpty) { // set the predicate to false to prevent posting to HAT
      hatClient.postData(namespace, endpoint, batchdata)
        .map(_ => Done)
        .recoverWith {
          case _: DuplicateDataException => Future.successful(Done)
          case e: ApiException           => Future.failed(HATApiCommunicationException(e.getMessage, e))
          case e                         => Future.failed(HATApiCommunicationException("Unknown error", e))
        }
    }
    else {
      Future.successful(Done)
    }
  }

  //protected def validateMinDataStructure(rawData: JsValue, hatAddress: String): Try[JsArray]

  //protected def validateMinDataStructure(rawData: JsValue): Try[JsArray]

  // TODO: remove single parameter interface once all of the plugs are made compatible

  /**
   * Validates whether dataset meets minimum field requirements by trying to cast it into a case class
   *
   * @param rawData JSON value of data to be validated
   * @return Try block - successful if the data matches minimum structure requirements
   */
  protected def validateMinDataStructure(rawData: JsValue): Try[JsArray] =
    throw new NotImplementedError("Implementation missing for abstract method \"validateMinDataStructure\"")

  /**
   * Validates whether dataset meets minimum field requirements by trying to cast it into a case class
   *
   * @param rawData JSON value of data to be validated
   * @param hatAddress String value of the hat address requesting the data. Used to log stuff
   * @return Try block - successful if the data matches minimum structure requirements
   */
  protected def validateMinDataStructure(rawData: JsValue, hatAddress: String): Try[JsArray] =
    throw new NotImplementedError("Implementation missing for abstract method \"validateMinDataStructure\"")

  /**
   * Extract timestamp of data record to be stored in the HAT - HAT allows timestamp fields to
   * be set in the right format for easier handling later
   *
   * @param content JSON value of a successful response
   * @return DateTime-formatted timestamp - now by default
   */
  protected def extractRecordTimestamp(content: JsValue): DateTime = DateTime.now()

  /**
   * Build data fetch continuation API call parameters if the source has data paging.
   * Responsible for checking if there should be a further call for fetching data and configuring the endpoint
   *
   * @param content JSON value of a successful response
   * @param params pre-set API endpoint parameters to build from
   * @return Optional API endpoint configuration for continuation - None means no continuation
   */
  protected def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall]

  /**
   * Set up API call details for next synchronisation - set up any fields available at the last operation of the current
   * synchronisation round.
   *
   * @param content JSON value of a successful response
   * @param params pre-set API endpoint parameters to build from
   * @return Optional API endpoint configuration for continuation - None means no continuation
   */
  protected def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall

  /**
   * Asynchronously save endpoint status for a given hat address if data has been fetched successfully.
   *
   * @param content JSON value of a successful response
   * @param hatAddress address of the HAT for which to update plug status
   */
  //  protected def saveEndpointStatus(content: JsValue, hatAddress: String): Future[Unit]
}

