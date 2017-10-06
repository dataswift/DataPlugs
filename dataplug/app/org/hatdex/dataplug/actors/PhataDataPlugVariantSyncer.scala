/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.actors

import akka.actor.{ Actor, ActorLogging, ActorRef, PoisonPill, Props, Scheduler }
import akka.pattern.pipe
import org.hatdex.dataplug.actors.DataPlugManagerActor._
import org.hatdex.dataplug.apiInterfaces._
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariant
import org.hatdex.dataplug.services.DataPlugEndpointService
import org.hatdex.dataplug.utils.Mailer
import play.api.Logger
import play.api.libs.concurrent.InjectedActorSupport
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

class PhataDataPlugVariantSyncer(
    phata: String,
    endpointInterface: DataPlugEndpointInterface,
    apiEndpointVariant: ApiEndpointVariant,
    wsClient: WSClient,
    hatClient: ActorRef,
    val dataplugEndpointService: DataPlugEndpointService,
    val mailer: Mailer,
    val executionContext: ExecutionContext) extends Actor with ActorLogging with InjectedActorSupport with DataPlugManagerOperations {

  val scheduler: Scheduler = context.system.scheduler

  val logger: Logger = Logger(this.getClass)

  implicit val ec: ExecutionContext = executionContext

  def receive: Receive = {
    case Fetch(endpointCall, retries) =>
      logger.debug(s"FETCH Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}")
      context.become(syncing)
      fetch(endpointInterface, apiEndpointVariant, phata, endpointCall, retries, hatClient) pipeTo self
    case message =>
      logger.debug(s"UNKNOWN Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $message")
  }

  def syncing: Receive = {
    case FetchContinuation(fetchEndpoint, retries) =>
      logger.debug(s"FETCH CONTINUATION Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}")
      fetchContinuation(endpointInterface, apiEndpointVariant, phata, fetchEndpoint, retries, hatClient) pipeTo self
    case Complete(fetchEndpoint) =>
      logger.debug(s"COMPLETE Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant} in FETCH")
      context.become(receive)
      complete(endpointInterface, apiEndpointVariant, phata, fetchEndpoint)
      context.parent ! Completed(apiEndpointVariant, phata)
    case SyncingFailed(error) =>
      logger.warn(s"FAILED Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $error")

      mailer.serverExceptionNotifyInternal(
        s"FAILED Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $error",
        new RuntimeException(error))

      context.parent ! Failed(apiEndpointVariant, phata, error)
      self ! PoisonPill
    case message =>
      logger.debug(s"UNKNOWN Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $message")
  }

}

object PhataDataPlugVariantSyncer {
  def props(
    phata: String,
    endpointInterface: DataPlugEndpointInterface,
    apiEndpointVariant: ApiEndpointVariant,
    wsClient: WSClient,
    hatClient: ActorRef,
    dataplugEndpointService: DataPlugEndpointService,
    mailer: Mailer,
    executionContext: ExecutionContext): Props =
    Props(new PhataDataPlugVariantSyncer(phata, endpointInterface, apiEndpointVariant,
      wsClient, hatClient, dataplugEndpointService, mailer, executionContext))
}