/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 5, 2017
 */

package com.hubofallthings.dataplug.actors

import javax.inject.{ Inject, Named }
import akka.actor.{ Actor, ActorRef, Cancellable, PoisonPill, Props, Scheduler }
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import com.hubofallthings.dataplug.actors.Errors.{ DataPlugError, HATApiForbiddenException }
import com.hubofallthings.dataplug.apiInterfaces.{ DataPlugEndpointInterface, DataPlugRegistry }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.models.User
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager, HatTokenService }
import com.hubofallthings.dataplug.utils.{ AuthenticatedHatClient, Mailer }
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Logger }

import java.io.{ PrintWriter, StringWriter }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object DataPlugManagerActor {

  val failureRetryInterval: FiniteDuration = 30.seconds
  val maxFetchRetries: Int = 5

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
      error: DataPlugError) extends DataPlugManagerActorMessage

  case class CancelSchedule(
      endpoint: ApiEndpointVariant,
      phata: String) extends DataPlugManagerActorMessage

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
    dataplugSyncerActorManager: DataplugSyncerActorManager,
    val dataplugEndpointService: DataPlugEndpointService,
    val mailer: Mailer,
    @Named("syncThrottler") throttledSyncActor: ActorRef)(implicit val ec: ExecutionContext) extends Actor
  with DataPlugManagerOperations
  with RetryingActorLauncher {

  protected val logger: Logger = Logger(this.getClass)
  protected implicit val materializer: ActorMaterializer = ActorMaterializer()
  protected val apiVersion = "v2.6"

  import DataPlugManagerActor._

  val scheduler: Scheduler = context.system.scheduler
  var syncingSchedules: Map[(String, String, String), Cancellable] = Map()

  private def syncerActorKey(phata: String, variant: ApiEndpointVariant) = s"$phata-${variant.endpoint.sanitizedName}-${variant.sanitizedVariantName}"

  def receive: Receive = {
    case Start(variant, phata, maybeEndpointCall) =>
      dataPlugRegistry.get[DataPlugEndpointInterface](variant.endpoint.name).map { endpointInterface =>
        val actorKey = syncerActorKey(phata, variant)
        logger.debug(s"Starting actor fetch $actorKey")
        Try(createSyncingSchedule(phata, endpointInterface, variant, maybeEndpointCall))
          .map { syncingSchedule =>
            logger.info(s"Started syncing actor for $phata, $endpointInterface")

            val syncJobIdentifier = (phata, variant.endpoint.name, variant.variant.getOrElse(""))

            syncingSchedules.get(syncJobIdentifier).map { priorSchedule =>
              logger.debug(s"Cancelling previous schedule for $syncJobIdentifier variant")
              priorSchedule.cancel()
            }

            logger.debug(s"Creating new schedule for $syncJobIdentifier variant")
            syncingSchedules = syncingSchedules + (syncJobIdentifier -> syncingSchedule)
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

    case CancelSchedule(variant, phata) =>
      val jobIdentifier = (phata, variant.endpoint.name, variant.variant.getOrElse(""))
      logger.info(s"Cancelling previous schedule $jobIdentifier on request.")
      syncingSchedules.get(jobIdentifier).map(_.cancel())

    case Stop(variant, phata) =>
      val actorKey = s"${syncerActorKey(phata, variant)}-supervisor"
      logger.warn(s"Stopping actor $actorKey")
      // Kill any actors that are syncing this dataplug variant for phata
      context.actorSelection(actorKey) ! PoisonPill

    case Failed(variant, phata, exception) =>

      val actorKey = s"${syncerActorKey(phata, variant)}-supervisor"
      val syncJobIdentifier = (phata, variant.endpoint.name, variant.variant.getOrElse(""))
      // cancel any scheculed actors
      syncingSchedules.get(syncJobIdentifier).map { priorSchedule =>
        logger.debug(s"Cancelling previous schedule for $syncJobIdentifier variant")
        priorSchedule.cancel()
      }
      // stop actor
      context.actorSelection(actorKey) ! PoisonPill

      // update db
      logger.error(s"Stopping actor $actorKey due to a failed exception: $exception")
      exception match {
        // Kill any actors that are syncing this dataplug variant for phata
        case _: HATApiForbiddenException =>
          val choice = ApiEndpointVariantChoice(variant.endpoint.name, variant.endpoint.description, false, variant)
          dataplugSyncerActorManager.updateApiVariantChoices(User("", phata, List()), Seq(choice))
      }
  }

  private def createSyncingSchedule(
    phata: String,
    endpointInterface: DataPlugEndpointInterface,
    variant: ApiEndpointVariant,
    maybeEndpointCall: Option[ApiEndpointCall]): Cancellable = {
    val actorKey = syncerActorKey(phata, variant)
    context.system.scheduler.schedule(1.second, endpointInterface.refreshInterval) {

      hatTokenService.forUser(phata) flatMap {
        case Some(hatCredentials) =>
          val eventualSyncMessage = for {
            endpointCall <- dataplugEndpointService.retrieveLastSuccessfulEndpointVariant(phata, variant.endpoint.name, variant.variant)
              .map(maybeEndpointVariant => maybeEndpointVariant.flatMap(_.configuration).orElse(maybeEndpointCall))
            syncerActor <- startSyncerActor(phata, hatCredentials.accessToken, variant, endpointInterface, actorKey)
          } yield Forward(Fetch(endpointCall, 0), syncerActor)

          eventualSyncMessage.recover {
            case e =>
              logger.error(s"Error while trying to start syncing data for $phata, variant $variant", e)
              DataPlugManagerActor.Nop()
          } pipeTo throttledSyncActor

        case None =>
          logger.error(s"HAT token missing/expired for $phata. Cancelling schedule.")
          Future.successful(DataPlugManagerActor.CancelSchedule(variant, phata)) pipeTo self
      }
    }
  }

  private def startSyncerActor(phata: String, accessToken: String, variant: ApiEndpointVariant, endpointInterface: DataPlugEndpointInterface, actorKey: String): Future[ActorRef] = {
    val protocol = if (configuration.get[Boolean]("hat.secure")) "https://" else "http://"
    val hatClient: AuthenticatedHatClient = new AuthenticatedHatClient(ws, phata, protocol, accessToken)

    logger.debug(s"Creating syncer actor for $phata, protocol - $protocol, access token - $accessToken")

    val syncActorProps: Props =
      PhataDataPlugVariantSyncer.props(phata, endpointInterface, variant, ws,
        hatClient, throttledSyncActor, dataplugEndpointService, mailer)(ec)

    launchActor(syncActorProps, s"$actorKey")
  }
}
