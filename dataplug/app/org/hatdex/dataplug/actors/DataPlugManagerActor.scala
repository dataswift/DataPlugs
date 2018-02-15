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

import akka.actor.{ Actor, ActorRef, Cancellable, PoisonPill, Props, Scheduler }
import akka.pattern.{ Backoff, BackoffSupervisor, pipe }
import akka.stream.ActorMaterializer
import org.hatdex.dataplug.actors.Errors.DataPlugError
import org.hatdex.dataplug.apiInterfaces._
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointVariant }
import org.hatdex.dataplug.services.DataPlugEndpointService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dex.api.services.DexClient
import org.hatdex.dexter.actors.HatClientActor
import org.hatdex.dexter.models.HatClientCredentials
import play.api.cache.SyncCacheApi
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Logger }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Success, Try }

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

  sealed trait DataPlugSyncDispatcherActorMessage

  case class Forward(message: PhataDataPlugVariantSyncerMessage, to: ActorRef) extends DataPlugSyncDispatcherActorMessage
  case class Nop() extends DataPlugSyncDispatcherActorMessage

  sealed trait PhataDataPlugVariantSyncerMessage

  case class Fetch(call: Option[ApiEndpointCall], retries: Int) extends PhataDataPlugVariantSyncerMessage

  case class FetchContinuation(continuation: ApiEndpointCall, retries: Int) extends PhataDataPlugVariantSyncerMessage

  case class Complete(nextSyncCall: ApiEndpointCall) extends PhataDataPlugVariantSyncerMessage

  case class SyncingFailed(error: String, cause: DataPlugError) extends PhataDataPlugVariantSyncerMessage

}

class DataPlugManagerActor @Inject() (
    dataPlugRegistry: DataPlugRegistry,
    ws: WSClient,
    configuration: Configuration,
    cacheApi: SyncCacheApi,
    val dataplugEndpointService: DataPlugEndpointService,
    val mailer: Mailer,
    @Named("syncThrottler") throttledSyncActor: ActorRef)(implicit val ec: ExecutionContext) extends Actor
  with DataPlugManagerOperations
  with RetryingActorLauncher {

  protected val logger: Logger = Logger(this.getClass)
  protected implicit val materializer: ActorMaterializer = ActorMaterializer()

  import DataPlugManagerActor._

  val scheduler: Scheduler = context.system.scheduler

  private def syncerActorKey(phata: String, variant: ApiEndpointVariant) = s"$phata-${variant.endpoint.sanitizedName}-${variant.sanitizedVariantName}"

  def receive: Receive = {
    case Start(variant, phata, maybeEndpointCall) =>
      dataPlugRegistry.get[DataPlugEndpointInterface](variant.endpoint.name).map { endpointInterface =>
        val actorKey = syncerActorKey(phata, variant)
        logger.info(s"Starting actor fetch $actorKey")
        Try(createSyncingSchedule(phata, endpointInterface, variant, maybeEndpointCall))
          .map { _ =>
            logger.warn(s"Started syncing actor for $phata, $endpointInterface")
          }
          .recover {
            case e => logger.warn(s"Creating actor syncing schedule for $phata, $endpointInterface failed: ${e.getMessage}")
          }
      } getOrElse {
        val message = s"No such plug ${variant.endpoint.name} in DataPlug Registry"
        logger.error(message)
        mailer.serverExceptionNotifyInternal(message, new DataPlugError(message))
        Future.successful(())
      }

    case Stop(variant, phata) =>
      val actorKey = s"${syncerActorKey(phata, variant)}-supervisor"
      logger.warn(s"Stopping actor $actorKey")
      // Kill any actors that are syncing this dataplug variant for phata
      context.actorSelection(actorKey) ! PoisonPill
  }

  private val dexClient = new DexClient(
    ws,
    configuration.get[String]("service.dex.address"),
    configuration.get[String]("service.dex.scheme"))

  private def createSyncingSchedule(
    phata: String,
    endpointInterface: DataPlugEndpointInterface,
    variant: ApiEndpointVariant,
    maybeEndpointCall: Option[ApiEndpointCall]): Cancellable = {
    val actorKey = syncerActorKey(phata, variant)
    context.system.scheduler.schedule(1.second, endpointInterface.refreshInterval) {

      val eventualSyncMessage = for {
        endpointCall <- dataplugEndpointService.retrieveLastSuccessfulEndpointVariant(phata, variant.endpoint.name, variant.variant)
          .map(maybeEndpointVariant => maybeEndpointVariant.flatMap(_.configuration).orElse(maybeEndpointCall))
        syncerActor <- startSyncerActor(phata, variant, endpointInterface, actorKey)
      } yield Forward(Fetch(endpointCall, 0), syncerActor)

      eventualSyncMessage.onFailure {
        case e => logger.error(s"Error while trying to start sycning dat for $phata, variant $variant: ${e.getMessage}", e)
      }

      eventualSyncMessage.recover {
        case _ => DataPlugManagerActor.Nop()
      } pipeTo throttledSyncActor
    }
  }

  private def startSyncerActor(phata: String, variant: ApiEndpointVariant, endpointInterface: DataPlugEndpointInterface, actorKey: String): Future[ActorRef] = {
    val hatActorProps = HatClientActor.props(ws, phata, HatClientCredentials(
      configuration.get[String]("service.hatCredentials.username"),
      configuration.get[String]("service.hatCredentials.password"),
      secure = true))

    val hatActorSupervisorProps = BackoffSupervisor.props(
      // Backing off supervisor with 20% "noise" to vary the intervals slightly
      Backoff.onStop(hatActorProps, childName = actorKey,
        minBackoff = 10.seconds, maxBackoff = 120.seconds, randomFactor = 0.2))

    val cacheKey = s"dex:plug:${configuration.get[String]("service.dex.dataplugId")}:$phata"
    val dexConnected = cacheApi.get[Boolean](cacheKey)
      .map { setup =>
        Future.successful(setup)
      } getOrElse {
        dexClient.dataplugConnectHat(
          configuration.get[String]("service.dex.accessToken"),
          UUID.fromString(configuration.get[String]("service.dex.dataplugId")), phata)
          .map(_ => true)
          .andThen { case Success(s) => cacheApi.set(cacheKey, s) }
          .andThen { case Success(_) => logger.warn(s"DEX connected dataplug ${configuration.get[String]("service.dex.dataplugId")} to HAT $phata") }
      }

    def syncerActor(hatActor: ActorRef): Props = {
      val syncerProps = PhataDataPlugVariantSyncer.props(phata, endpointInterface, variant,
        ws, hatActor, throttledSyncActor, dataplugEndpointService, mailer)(ec)

      BackoffSupervisor.props(
        // Backing off supervisor with 20% "noise" to vary the intervals slightly
        Backoff.onStop(syncerProps, childName = actorKey,
          minBackoff = 10.seconds, maxBackoff = 120.seconds, randomFactor = 0.2))
    }

    for {
      _ <- dexConnected
      hatActor <- launchActor(hatActorSupervisorProps, s"hat:$phata")
      syncerActor <- launchActor(syncerActor(hatActor), s"$actorKey-supervisor")
    } yield syncerActor
  }
}
