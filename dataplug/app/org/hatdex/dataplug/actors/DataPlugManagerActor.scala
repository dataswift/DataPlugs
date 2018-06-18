/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 5, 2017
 */

package org.hatdex.dataplug.actors

import javax.inject.{ Inject, Named }
import akka.actor.{ Actor, ActorRef, Cancellable, PoisonPill, Props, Scheduler }
import akka.pattern.{ Backoff, BackoffSupervisor, pipe }
import akka.stream.ActorMaterializer
import org.hatdex.dataplug.actors.Errors.DataPlugError
import org.hatdex.dataplug.apiInterfaces._
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointVariant }
import org.hatdex.dataplug.services.{ DataPlugEndpointService, HatTokenService }
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplug.models.HatAccessCredentials
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Logger }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

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
    hatTokenService: HatTokenService,
    ws: WSClient,
    configuration: Configuration,
    cacheApi: AsyncCacheApi,
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
        logger.debug(s"Starting actor fetch $actorKey")
        Try(createSyncingSchedule(phata, endpointInterface, variant, maybeEndpointCall))
          .map { _ =>
            logger.info(s"Started syncing actor for $phata, $endpointInterface")
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
        maybeAccessToken <- hatTokenService.forUser(phata) if maybeAccessToken.isDefined
        syncerActor <- startSyncerActor(phata, maybeAccessToken.get.accessToken, variant, endpointInterface, actorKey)
      } yield Forward(Fetch(endpointCall, 0), syncerActor)

      eventualSyncMessage.recover {
        case e =>
          logger.error(s"Error while trying to start sycning dat for $phata, variant $variant: ${e.getMessage}", e)
          DataPlugManagerActor.Nop()
      } pipeTo throttledSyncActor
    }
  }

  private def startSyncerActor(phata: String, accessToken: String, variant: ApiEndpointVariant, endpointInterface: DataPlugEndpointInterface, actorKey: String): Future[ActorRef] = {
    val protocol = if (configuration.get[Boolean]("hat.secure")) "https://" else "http://"
    val hatActorProps = HatClientActor.props(ws, protocol, HatAccessCredentials(phata, accessToken))
    logger.debug(s"Creating syncer actor for $phata, protocol - $protocol, access token - $accessToken")

    def syncActor(hatActor: ActorRef): Props = {
      PhataDataPlugVariantSyncer.props(phata, endpointInterface, variant, ws,
        hatActor, throttledSyncActor, dataplugEndpointService, mailer)(ec)
    }

    for {
      hatActor <- launchActor(hatActorProps, s"hat:$phata")
      syncActor <- launchActor(syncActor(hatActor), s"$actorKey")
    } yield syncActor
  }
}
