/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.actors

import akka.Done
import akka.actor.{ ActorRef, Scheduler }
import akka.pattern.after
import akka.util.Timeout
import org.hatdex.dataplug.actors.Errors.DataPlugError
import org.hatdex.dataplug.apiInterfaces._
import org.hatdex.dataplug.apiInterfaces.models._
import org.hatdex.dataplug.services.DataPlugEndpointService
import play.api.Logger

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugManagerOperations {
  protected implicit val ec: ExecutionContext

  protected val dataplugEndpointService: DataPlugEndpointService
  protected val scheduler: Scheduler
  protected def logger: Logger

  import DataPlugManagerActor._

  def fetchData(
    endpointInterface: DataPlugEndpointInterface,
    variant: ApiEndpointVariant, phata: String,
    fetchEndpoint: ApiEndpointCall, retries: Int, hatClient: ActorRef): Future[PhataDataPlugVariantSyncerMessage] = {
    implicit val fetchTimeout: Timeout = 30.seconds
    endpointInterface.fetch(fetchEndpoint, phata, hatClient) map {
      case DataPlugFetchContinuation(continuation) =>
        dataplugEndpointService.saveEndpointStatus(phata, variant, fetchEndpoint, success = true, Some("continuation continued"))
        FetchContinuation(continuation, 0)
      case DataPlugFetchNextSync(nextSync) =>
        dataplugEndpointService.saveEndpointStatus(phata, variant, fetchEndpoint, success = true, Some("continuation finished"))
        Complete(nextSync)
    }
  }

  def fetch(
    endpointInterface: DataPlugEndpointInterface,
    variant: ApiEndpointVariant, phata: String,
    endpointCall: Option[ApiEndpointCall], retries: Int, hatClient: ActorRef): Future[PhataDataPlugVariantSyncerMessage] = {

    endpointInterface.buildFetchParameters(endpointCall.orElse(variant.configuration)) flatMap { fetchEndpoint =>
      fetchData(endpointInterface, variant, phata, fetchEndpoint, retries, hatClient) recoverWith {
        case e: DataPlugError => fetchError(e, variant, phata, fetchEndpoint, retries, hatClient)
      }
    } recoverWith {
      case error: DataPlugError =>
        val message = s"${error.getClass.getSimpleName} Error while fetching endpoint parameters: ${error.getMessage}"
        logger.error(message)
        Future.successful(SyncingFailed(message, error))
    }

  }

  def fetchContinuation(
    endpointInterface: DataPlugEndpointInterface,
    variant: ApiEndpointVariant, phata: String,
    fetchEndpoint: ApiEndpointCall, retries: Int, hatClient: ActorRef): Future[PhataDataPlugVariantSyncerMessage] = {

    fetchData(endpointInterface, variant, phata, fetchEndpoint, retries, hatClient) recoverWith {
      case e: DataPlugError =>
        fetchError(e, variant, phata, fetchEndpoint, retries, hatClient)
    }
  }

  protected def fetchError(
    e: DataPlugError, variant: ApiEndpointVariant, phata: String,
    fetchEndpoint: ApiEndpointCall, retries: Int, hatClient: ActorRef): Future[PhataDataPlugVariantSyncerMessage] = {

    logger.warn(s"${e.getClass.getSimpleName} Error fetching ${variant.endpoint.name} for $phata: ${e.getMessage}")
    if (retries < maxFetchRetries) {
      dataplugEndpointService.saveEndpointStatus(phata, variant,
        fetchEndpoint, success = false, Some(s"Retry $retries for data fetching"))

      after((retries + 1) * failureRetryInterval, using = scheduler) {
        Future.successful(FetchContinuation(fetchEndpoint, retries + 1))
      }
    }
    else {
      val message = s"${e.getClass.getSimpleName} Error fetching ${variant.endpoint.name} for $phata - maximum of $maxFetchRetries retries exceeded, error ${e.getMessage}"
      logger.error(message)
      dataplugEndpointService.saveEndpointStatus(phata, variant,
        fetchEndpoint, success = false, Some(s"data fetch failed: ${e.getMessage}"))
      Future.successful(SyncingFailed(message, e))
    }
  }

  def complete(
    endpointInterface: DataPlugEndpointInterface,
    variant: ApiEndpointVariant, phata: String,
    nextSyncCall: ApiEndpointCall): Future[Done] = {

    logger.info(s"${variant.endpoint.name} Completed Fetching for $phata, next sync $nextSyncCall")

    dataplugEndpointService.activateEndpoint(phata, variant.endpoint.name, variant.variant, Some(nextSyncCall))
  }
}

object Errors {
  class DataPlugError(message: String = "", cause: Throwable = None.orNull) extends Exception(message, cause)

  class HATApiError(message: String = "", cause: Throwable = None.orNull) extends DataPlugError(message, cause)

  case class HATAuthenticationException(message: String = "", cause: Throwable = None.orNull) extends HATApiError(message, cause)
  case class HATDataProcessingException(message: String = "", cause: Throwable = None.orNull) extends HATApiError(message, cause)
  case class HATApiCommunicationException(message: String = "", cause: Throwable = None.orNull) extends HATApiError(message, cause)

  class SourceApiError(message: String = "", cause: Throwable = None.orNull) extends DataPlugError(message, cause)

  case class SourceAuthenticationException(message: String = "", cause: Throwable = None.orNull) extends SourceApiError(message, cause)
  case class SourceDataProcessingException(message: String = "", cause: Throwable = None.orNull) extends SourceApiError(message, cause)
  case class SourceApiCommunicationException(message: String = "", cause: Throwable = None.orNull) extends SourceApiError(message, cause)
}
