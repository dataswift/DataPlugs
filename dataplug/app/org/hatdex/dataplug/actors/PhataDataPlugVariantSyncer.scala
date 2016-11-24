package org.hatdex.dataplug.actors

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.pattern.pipe
import org.hatdex.dataplug.actors.DataPlugManagerActor._
import org.hatdex.dataplug.apiInterfaces._
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariant
import org.hatdex.dataplug.models.HatClientCredentials
import org.hatdex.dataplug.services.DataPlugEndpointService
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.ExecutionContext

class PhataDataPlugVariantSyncer(
    phata: String,
    endpointInterface: DataPlugEndpointInterface,
    apiEndpointVariant: ApiEndpointVariant,
    configuration: Configuration,
    hatClientFactory: InjectedHatClientActor.Factory,
    val dataplugEndpointService: DataPlugEndpointService,
    val executionContext: ExecutionContext) extends Actor with ActorLogging with InjectedActorSupport with DataPlugManagerOperations {

  val scheduler = context.system.scheduler

  sealed trait DataPlugSyncState
  case object DataPlugSyncing extends DataPlugSyncState
  case object DataPlugFailed extends DataPlugSyncState
  case object DataPlugIdle extends DataPlugSyncState
  case object DataPlugStopped extends DataPlugSyncState

  implicit val ec = executionContext

  var state: DataPlugSyncState = DataPlugIdle
  var hatClient: ActorRef = createHatClientActor(phata, HatClientCredentials("dataplug", "password"))

  def receive: Receive = {
    case Fetch(endpointCall, retries) =>
      log.debug(s"FETCH Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}")
      state = DataPlugSyncing
      context.become(syncing)
      fetch(endpointInterface, apiEndpointVariant, phata, endpointCall, retries, hatClient) pipeTo self
    case Complete(fetchEndpoint) =>
      log.debug(s"COMPLETE Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant} in FETCH")
      state = DataPlugIdle
      complete(endpointInterface, apiEndpointVariant, phata, fetchEndpoint) pipeTo self
      context.parent ! Completed(apiEndpointVariant, phata)
    case SyncingFailed(error) =>
      log.debug(s"FAILED Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $error")
      state = DataPlugFailed
      context.parent ! Failed(apiEndpointVariant, phata, error)
      context stop self
    case message =>
      log.debug(s"UNKNOWN Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $message")
  }

  def syncing: Receive = {
    case FetchContinuation(fetchEndpoint, retries) =>
      log.debug(s"FETCH CONTINUATION Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}")
      state = DataPlugSyncing
      fetchContinuation(endpointInterface, apiEndpointVariant, phata, fetchEndpoint, retries, hatClient) pipeTo self
    case Complete(fetchEndpoint) =>
      log.debug(s"COMPLETE Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant} in FETCH")
      state = DataPlugIdle
      context.become(receive)
      complete(endpointInterface, apiEndpointVariant, phata, fetchEndpoint)
      context.parent ! Completed(apiEndpointVariant, phata)
    case SyncingFailed(error) =>
      log.debug(s"FAILED Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $error")
      state = DataPlugFailed
      context.parent ! Failed(apiEndpointVariant, phata, error)
      context stop self
    case message =>
      log.debug(s"UNKNOWN Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $message")
  }

  private def createHatClientActor(hatAddress: String, credentials: HatClientCredentials): ActorRef = {
    injectedChild(
      hatClientFactory(hatAddress, configuration.underlying, credentials), hatAddress,
      props = (props: Props) => props.withDispatcher("hat-client-actor-dispatcher"))
  }
}

object PhataDataPlugVariantSyncer {
  def props(
    phata: String,
    endpointInterface: DataPlugEndpointInterface,
    apiEndpointVariant: ApiEndpointVariant,
    configuration: Configuration,
    hatClientFactory: InjectedHatClientActor.Factory,
    dataplugEndpointService: DataPlugEndpointService,
    executionContext: ExecutionContext): Props =
    Props(new PhataDataPlugVariantSyncer(phata, endpointInterface, apiEndpointVariant,
      configuration, hatClientFactory, dataplugEndpointService, executionContext))
}