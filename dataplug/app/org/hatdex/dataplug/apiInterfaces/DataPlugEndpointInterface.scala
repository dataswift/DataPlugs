/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import akka.pattern.ask
import akka.util.Timeout
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticator
import org.hatdex.dataplug.apiInterfaces.models._
import org.hatdex.dataplug.utils.FutureRetries
import org.hatdex.dexter.actors.HatClientActor
import org.hatdex.dexter.actors.HatClientActor.{ DataSaved, FetchingFailed }
import org.joda.time.DateTime
import play.api.http.Status._
import play.api.libs.json.{ JsArray, JsValue, Json }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait DataPlugEndpointInterface extends HatDataOperations with RequestAuthenticator with DataPlugApiEndpointClient {
  val refreshInterval: FiniteDuration
  protected implicit val scheduler: Scheduler

  /**
   * Fetch data from an API endpoint as per parametrised configuration, for a specific HAT client
   *
   * @param fetchParams API endpoint parameters generic (stateless) for the endpoint
   * @param hatAddress HAT Address (domain)
   * @param hatClientActor HAT client actor for specific HAT
   * @return Potentially updated set of parameters, e.g. with new timestamps
   */
  def fetch(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): Future[DataPlugFetchStep] = {
    processDataFetch(fetchParams, hatAddress, hatClientActor, retrying = false)
  }

  protected def processDataFetch(
    fetchParams: ApiEndpointCall,
    hatAddress: String,
    hatClientActor: ActorRef,
    retrying: Boolean)(implicit ec: ExecutionContext, timeout: Timeout): Future[DataPlugFetchStep] = {
    val authenticatedFetchParameters = authenticateRequest(fetchParams, hatAddress, refreshToken = retrying)

    authenticatedFetchParameters flatMap { requestParameters =>
      buildRequest(requestParameters)
    } flatMap { result =>
      result.status match {
        case OK =>
          processResults(result.json, hatAddress, hatClientActor, fetchParams) map { _ =>
            logger.debug(s"Successfully processed request for $hatAddress to save data from ${fetchParams.url}${fetchParams.path}")
            buildContinuation(result.json, fetchParams)
              .map(DataPlugFetchContinuation)
              .getOrElse(DataPlugFetchNextSync(buildNextSync(result.json, fetchParams)))
          } recoverWith {
            case error =>
              mailer.serverExceptionNotifyInternal(
                s"""
                   | Error when uploading data to HAT $hatAddress.
                   | Fetch Parameters: $fetchParams.
                   | Content: ${Json.prettyPrint(result.json)}
                """.stripMargin, error)
              Future.failed(error)
          }

        case UNAUTHORIZED =>
          if (!retrying) {
            logger.debug(s"Unauthorized request $fetchParams for $hatAddress - ${result.status}: ${result.body}")
            processDataFetch(fetchParams, hatAddress, hatClientActor, retrying = true)
          }
          else {
            logger.warn(s"Unauthorized retried request $fetchParams for $hatAddress - ${result.status}: ${result.body}")
            Future.successful(DataPlugFetchNextSync(fetchParams))
          }
        case NOT_FOUND =>
          logger.warn(s"Not found for request $fetchParams - ${result.status}: ${result.body}")
          Future.failed(new RuntimeException(s"Not found for request $fetchParams for $hatAddress - ${result.status}: ${result.body}"))
        case _ =>
          logger.warn(s"Unsuccessful response from api endpoint $fetchParams for $hatAddress - ${result.status}: ${result.body}")
          Future.successful(DataPlugFetchNextSync(fetchParams))
      }
    } recoverWith {
      case e =>
        logger.warn(s"Error when querying api endpoint $fetchParams for $hatAddress - ${e.getMessage}")
        Future.failed(e)
    }
  }

  protected def processResults(content: JsValue, hatAddress: String, hatClientActor: ActorRef, fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {
    val retries: List[FiniteDuration] = FutureRetries.withJitter(List(20.seconds, 2.minutes, 5.minutes, 10.minutes), 0.2, 0.5)
    FutureRetries.retry(uploadHatData(namespace, endpoint, content, hatAddress, hatClientActor), retries)
  }

  protected def uploadHatData(
    namespace: String,
    endpoint: String,
    data: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {

    val batchdata: JsArray = data match {
      case v: JsArray => v
      case v: JsValue => JsArray(Seq(v))
    }

    if (batchdata.value.nonEmpty) { // set the predicate to false to prevent posting to HAT
      hatClientActor.?(HatClientActor.PostData(namespace, endpoint, batchdata))
        .map {
          case FetchingFailed(message) => Future.failed(new RuntimeException(message))
          case DataSaved(_)            => Future.successful(())
          case _                       => Future.failed(new RuntimeException("Unrecognised message from the HAT Client Actor"))
        }
    }
    else {
      Future.successful(())
    }
  }

  /**
   * Validates whether dataset meets minimum field requirements by trying to cast it into a case class
   *
   * @param rawData JSON value of data to be validated
   * @return Try block - successful if the data matches minimum structure requirements
   */

  protected def validateMinDataStructure(rawData: JsValue): Try[JsArray]

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

