/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */
package org.hatdex.dataplug.actors

import javax.inject.Inject

import akka.actor.{ Actor, ActorLogging, ActorNotFound, ActorRef, PoisonPill }
import akka.pattern.{ Backoff, BackoffSupervisor }
import org.hatdex.dataplug.apiInterfaces._
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointVariant }
import org.hatdex.dataplug.services.DataPlugEndpointService
import org.hatdex.dataplug.utils.Mailer
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport
import play.api.libs.ws.WSClient

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

object DataPlugManagerActor {

  val failureRetryInterval = 30.seconds
  val maxFetchRetries = 10

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
    val dataplugEndpointService: DataPlugEndpointService,
    val mailer: Mailer,
    hatClientFactory: InjectedHatClientActor.Factory) extends Actor with ActorLogging with InjectedActorSupport with DataPlugManagerOperations {

  import DataPlugManagerActor._

  implicit val executionContext: ExecutionContext = context.dispatcher
  val scheduler = context.system.scheduler

  sealed trait DataPlugSyncState
  case object DataPlugSyncing extends DataPlugSyncState
  case object DataPlugFailed extends DataPlugSyncState
  case object DataPlugIdle extends DataPlugSyncState
  case object DataPlugStopped extends DataPlugSyncState

  val dataPlugSyncActors: mutable.Map[String, ActorRef] = mutable.Map()

  def receive: Receive = {
    case Start(variant, phata, maybeEndpointCall) =>
      dataPlugRegistry.get[DataPlugEndpointInterface](variant.endpoint.name).map { endpointInterface =>
        val actorKey = s"$phata-${variant.endpoint.name}-${variant.variant.getOrElse("").replace("#", "")}"
        log.warning(s"Starting actor fetch $actorKey")
        startSyncerActor(phata, variant, endpointInterface, actorKey).map {
          case _ =>
            context.system.scheduler.schedule(0.seconds, endpointInterface.refreshInterval) {
              context.actorSelection(s"$actorKey-supervisor/$actorKey").resolveOne(5.seconds) flatMap { syncActor =>
                log.error(s"Starting fetch for sync actor $actorKey")
                val eventualFetchEndpointCall = dataplugEndpointService.retrieveLastSuccessfulEndpointVariant(
                  phata, variant.endpoint.name, variant.variant) map { maybeEndpointVariant =>
                  maybeEndpointVariant.flatMap(_.configuration).orElse(maybeEndpointCall)
                }

                eventualFetchEndpointCall.map(fetchEndpointCall => syncActor ! Fetch(fetchEndpointCall, 0))
              } recover {
                case e =>
                  val message = s"Could not fetch for actor $actorKey - ${e.getMessage}"
                  log.error(message)
                  mailer.serverExceptionNotifyInternal(message, new RuntimeException(message))
              }
            }
        }
      } getOrElse {
        val message = s"No such plug ${variant.endpoint.name} in DataPlug Registry"
        log.error(message)
        mailer.serverExceptionNotifyInternal(message, new RuntimeException(message))
      }
    case Stop(variant, phata) =>
      val actorKey = s"$phata-${variant.endpoint.name}-${variant.variant.getOrElse("").replace("#", "")}"
      log.warning(s"Stopping actor $actorKey")
      // Kill any actors that are syncing this dataplug variant for phata
      context.actorSelection(actorKey) ! PoisonPill
  }

  private def startSyncerActor(phata: String, variant: ApiEndpointVariant, endpointInterface: DataPlugEndpointInterface, actorKey: String): Future[ActorRef] = {
    context.actorSelection(s"$actorKey-supervisor").resolveOne(5.seconds) map { syncActor =>
      syncActor
    } recover {
      case ActorNotFound(selection) =>
        log.warning(s"Starting syncer actor $actorKey with supervisor - no existing $selection")
        val syncerProps = PhataDataPlugVariantSyncer.props(phata, endpointInterface, variant,
          configuration, hatClientFactory, ws, dataplugEndpointService, mailer, executionContext)

        val supervisor = BackoffSupervisor.props(
          Backoff.onStop(
            syncerProps,
            childName = actorKey,
            minBackoff = 3.seconds,
            maxBackoff = 30.seconds,
            randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
          ))

        context.actorOf(supervisor, s"$actorKey-supervisor")
    }
  }
}
