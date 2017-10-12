/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 5, 2017
 */

package org.hatdex.dataplug.actors

import java.util.UUID
import javax.inject.{ Inject, Named }

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorRef, PoisonPill, Props, Scheduler }
import akka.pattern.{ Backoff, BackoffSupervisor, pipe }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, OverflowStrategy, ThrottleMode }
import org.hatdex.dataplug.actors.DataPlugSyncDispatcherActor.Sync
import org.hatdex.dataplug.apiInterfaces._
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointVariant }
import org.hatdex.dataplug.services.DataPlugEndpointService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dex.api.services.DexClient
import org.hatdex.dexter.actors.HatClientActor
import org.hatdex.dexter.models.HatClientCredentials
import play.api.cache.CacheApi
import play.api.libs.concurrent.InjectedActorSupport
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Logger }

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

object DataPlugManagerActor {

  val failureRetryInterval: FiniteDuration = 30.seconds
  val maxFetchRetries: Int = 10

  sealed trait DataPlugManagerActorMessage

  case class Start(
    endpoint: ApiEndpointVariant,
    phata: String,
    call: Option[ApiEndpointCall]) extends DataPlugManagerActorMessage

  case class Stop(
    endpoint: ApiEndpointVariant,
    phata: String) extends DataPlugManagerActorMessage

  case class Completed(
    endpoint: ApiEndpointVariant,
    phata: String) extends DataPlugManagerActorMessage

  case class Failed(
    endpoint: ApiEndpointVariant,
    phata: String,
    error: String) extends DataPlugManagerActorMessage

  sealed trait PhataDataPlugVariantSyncerMessage

  case class Fetch(call: Option[ApiEndpointCall], retries: Int) extends PhataDataPlugVariantSyncerMessage

  case class FetchContinuation(continuation: ApiEndpointCall, retries: Int) extends PhataDataPlugVariantSyncerMessage

  case class Complete(nextSyncCall: ApiEndpointCall) extends PhataDataPlugVariantSyncerMessage

  case class SyncingFailed(error: String) extends PhataDataPlugVariantSyncerMessage

}

class DataPlugManagerActor @Inject() (
  dataPlugRegistry: DataPlugRegistry,
  ws: WSClient,
  configuration: Configuration,
  cacheApi: CacheApi,
  val dataplugEndpointService: DataPlugEndpointService,
  val mailer: Mailer,
  @Named("syncDispatcher") syncDispatcher: ActorRef) extends Actor
    with ActorLogging
    with InjectedActorSupport
    with DataPlugManagerOperations
    with RetryingActorLauncher {

  protected val logger: Logger = Logger(this.getClass)

  import DataPlugManagerActor._

  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = context.dispatcher
  val scheduler: Scheduler = context.system.scheduler

  sealed trait DataPlugSyncState
  case object DataPlugSyncing extends DataPlugSyncState
  case object DataPlugFailed extends DataPlugSyncState
  case object DataPlugIdle extends DataPlugSyncState
  case object DataPlugStopped extends DataPlugSyncState

  val dataPlugSyncActors: mutable.Map[String, ActorRef] = mutable.Map()

  val throttledSyncActor: ActorRef = Source.actorRef(bufferSize = 1000, OverflowStrategy.dropNew)
    .throttle(elements = 50, per = 15.minutes, maximumBurst = 3, ThrottleMode.Shaping)
    .to(Sink.actorRef(syncDispatcher, NotUsed))
    .run()

  def syncerActorKey(phata: String, variant: ApiEndpointVariant) = s"$phata-${variant.endpoint.sanitizedName}-${variant.sanitizedVariantName}"

  def receive: Receive = {
    case Start(variant, phata, maybeEndpointCall) =>
      dataPlugRegistry.get[DataPlugEndpointInterface](variant.endpoint.name).map { endpointInterface =>
        val actorKey = syncerActorKey(phata, variant)
        logger.warn(s"Starting actor fetch $actorKey")
        val eventualSyncMessage = for {
          endpointCall <- dataplugEndpointService.retrieveLastSuccessfulEndpointVariant(phata, variant.endpoint.name, variant.variant)
            .map(maybeEndpointVariant => maybeEndpointVariant.flatMap(_.configuration).orElse(maybeEndpointCall))
          syncerActor <- startSyncerActor(phata, variant, endpointInterface, actorKey)
        } yield Sync(syncerActor.path, endpointCall)

        eventualSyncMessage pipeTo throttledSyncActor
      } getOrElse {
        val message = s"No such plug ${variant.endpoint.name} in DataPlug Registry"
        logger.error(message)
        mailer.serverExceptionNotifyInternal(message, new RuntimeException(message))
        Future.successful(())
      }

    case Stop(variant, phata) =>
      val actorKey = syncerActorKey(phata, variant)
      logger.warn(s"Stopping actor $actorKey")
      // Kill any actors that are syncing this dataplug variant for phata
      context.actorSelection(actorKey) ! PoisonPill
  }

  private val dexClient = new DexClient(
    ws,
    configuration.getString("service.dex.address").get,
    configuration.getString("service.dex.scheme").get)

  private def startSyncerActor(phata: String, variant: ApiEndpointVariant, endpointInterface: DataPlugEndpointInterface, actorKey: String): Future[ActorRef] = {
    val hatActorProps = HatClientActor.props(ws, phata, HatClientCredentials(
      configuration.getString("service.hatCredentials.username").get,
      configuration.getString("service.hatCredentials.password").get,
      secure = true))

    val hatActorSupervisorProps = BackoffSupervisor.props(
      Backoff.onStop(
        hatActorProps,
        childName = actorKey,
        minBackoff = 10.seconds,
        maxBackoff = 120.seconds,
        randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
      ))

    val cacheKey = s"dex:plug:${configuration.getString("service.dex.dataplugId").get}:$phata"
    val dexConnected = cacheApi.get[Boolean](cacheKey)
      .map { setup =>
        Future.successful(setup)
      } getOrElse {
        dexClient.dataplugConnectHat(
          configuration.getString("service.dex.accessToken").get,
          UUID.fromString(configuration.getString("service.dex.dataplugId").get), phata)
          .map(_ => true)
          .andThen { case Success(s) => cacheApi.set(cacheKey, s) }
          .andThen { case Success(_) => logger.warn(s"DEX connected dataplug ${configuration.getString("service.dex.dataplugId").get} to HAT $phata") }
      }

    def syncerActor(hatActor: ActorRef): Props = {
      val syncerProps = PhataDataPlugVariantSyncer.props(phata, endpointInterface, variant,
        ws, hatActor, dataplugEndpointService, mailer, executionContext)

      BackoffSupervisor.props(
        Backoff.onStop(
          syncerProps,
          childName = actorKey,
          minBackoff = 10.seconds,
          maxBackoff = 120.seconds,
          randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
        ))
    }

    for {
      _ <- dexConnected
      hatActor <- launchActor(hatActorSupervisorProps, s"hat:$phata")
      syncerActor <- launchActor(syncerActor(hatActor), s"$actorKey-supervisor")
    } yield syncerActor
  }
}
