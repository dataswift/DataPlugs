/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.actors

import akka.actor.{ ActorRef, Scheduler }
import akka.event.LoggingAdapter
import akka.pattern.after
import akka.util.Timeout
import org.hatdex.dataplug.apiInterfaces._
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointVariant, DataPlugFetchContinuation, DataPlugFetchNextSync }
import org.hatdex.dataplug.services.DataPlugEndpointService

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugManagerOperations {
  implicit val executionContext: ExecutionContext

  protected val dataplugEndpointService: DataPlugEndpointService
  protected val scheduler: Scheduler
  protected def log: LoggingAdapter

  import DataPlugManagerActor._

  def kickstartAll(): Future[Seq[Start]] = {
    // Retrieve all endpoint variants any user is registered with
    dataplugEndpointService.retrieveAllEndpoints flatMap { userEndpoints =>
      val eventualFetches = userEndpoints map {
        case (phata, endpoint) =>
          // Retrieve last successful status for that user and endpoint variant
          val eventualMaybeEndpointVariant = dataplugEndpointService.retrieveLastSuccessfulEndpointVariant(phata, endpoint.endpoint.name, endpoint.variant)
          eventualMaybeEndpointVariant map { maybeEndpointVariant =>
            // If a fetch has previously been successful, fetch those settings as a starting point, otherwise use default configuration
            // FIXME: currently last successful endpoint doesn't contain continuation token or any such thing
            val fetchParameters = maybeEndpointVariant.flatMap(_.configuration).orElse(endpoint.configuration)
            Start(endpoint, phata, fetchParameters)
          }
      }
      Future.sequence(eventualFetches)
    }
  }

  def fetchData(
    endpointInterface: DataPlugEndpointInterface,
    variant: ApiEndpointVariant, phata: String,
    fetchEndpoint: ApiEndpointCall, retries: Int, hatClient: ActorRef): Future[PhataDataPlugVariantSyncerMessage] = {
    implicit val fetchTimeout: Timeout = 10.seconds;
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
        case e => fetchError(e, variant, phata, fetchEndpoint, retries, hatClient)
      }
    } recoverWith {
      case error =>
        log.error(s"Error while fetching endpoint parameters: ${error.getMessage}")
        Future.successful(SyncingFailed(s"Error while fetching endpoint parameters: ${error.getMessage}"))
    }

  }

  def fetchContinuation(
    endpointInterface: DataPlugEndpointInterface,
    variant: ApiEndpointVariant, phata: String,
    fetchEndpoint: ApiEndpointCall, retries: Int, hatClient: ActorRef): Future[PhataDataPlugVariantSyncerMessage] = {

    fetchData(endpointInterface, variant, phata, fetchEndpoint, retries, hatClient) recoverWith {
      case e =>
        fetchError(e, variant, phata, fetchEndpoint, retries, hatClient)
    }
  }

  protected def fetchError(
    e: Throwable, variant: ApiEndpointVariant, phata: String,
    fetchEndpoint: ApiEndpointCall, retries: Int, hatClient: ActorRef): Future[PhataDataPlugVariantSyncerMessage] = {

    log.error(s"Error fetching ${variant.endpoint.name} for $phata: ${e.getMessage}", e)
    if (retries < maxFetchRetries) {
      dataplugEndpointService.saveEndpointStatus(phata, variant,
        fetchEndpoint, success = false, Some(s"Retry $retries for data fetching"))

      after((retries + 1) * failureRetryInterval, using = scheduler) {
        Future.successful(FetchContinuation(fetchEndpoint, retries + 1))
      }
    }
    else {
      log.error(s"Error fetching ${variant.endpoint.name} for $phata - maximum of $maxFetchRetries retries exceeded, error ${e.getMessage}")
      dataplugEndpointService.saveEndpointStatus(phata, variant,
        fetchEndpoint, success = false, Some(s"data fetch failed: ${e.getMessage}"))
      Future.successful(SyncingFailed(s"Error fetching ${variant.endpoint.name} for $phata - maximum of $maxFetchRetries retries exceeded, error ${e.getMessage}"))
    }
  }

  def complete(
    endpointInterface: DataPlugEndpointInterface,
    variant: ApiEndpointVariant, phata: String,
    nextSyncCall: ApiEndpointCall): Future[Unit] = {

    log.info(s"${variant.endpoint.name} Completed Fetching for $phata, next sync $nextSyncCall")

    dataplugEndpointService.activateEndpoint(phata, variant.endpoint.name, variant.variant, Some(nextSyncCall))
  }
}
