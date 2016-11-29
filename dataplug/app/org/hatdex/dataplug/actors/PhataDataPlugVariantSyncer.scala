package org.hatdex.dataplug.actors

import java.util.UUID

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.pattern.pipe
import org.hatdex.dataplug.actors.DataPlugManagerActor._
import org.hatdex.dataplug.apiInterfaces._
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariant
import org.hatdex.dataplug.models.HatClientCredentials
import org.hatdex.dataplug.services.DataPlugEndpointService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.marketsquare.api.services.MarketsquareClient
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

class PhataDataPlugVariantSyncer(
    phata: String,
    endpointInterface: DataPlugEndpointInterface,
    apiEndpointVariant: ApiEndpointVariant,
    configuration: Configuration,
    hatClientFactory: InjectedHatClientActor.Factory,
    wsClient: WSClient,
    val dataplugEndpointService: DataPlugEndpointService,
    val mailer: Mailer,
    val executionContext: ExecutionContext) extends Actor with ActorLogging with InjectedActorSupport with DataPlugManagerOperations {

  val scheduler = context.system.scheduler

  sealed trait DataPlugSyncState
  case object DataPlugSyncing extends DataPlugSyncState
  case object DataPlugFailed extends DataPlugSyncState
  case object DataPlugIdle extends DataPlugSyncState
  case object DataPlugStopped extends DataPlugSyncState

  implicit val ec = executionContext

  var state: DataPlugSyncState = DataPlugIdle

  val msClient = new MarketsquareClient(
    wsClient,
    configuration.getString("service.marketsquare.address").get,
    configuration.getString("service.marketsquare.scheme").get)

  msClient.dataplugConnectHat(
    configuration.getString("service.marketsquare.accessToken").get,
    UUID.fromString(configuration.getString("service.marketsquare.dataplugId").get),
    phata)

  var hatClient: ActorRef = createHatClientActor(
    phata,
    HatClientCredentials(
      configuration.getString("service.hatCredentials.username").get,
      configuration.getString("service.hatCredentials.password").get))

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

      mailer.serverExceptionNotifyInternal(
        s"FAILED Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $error",
        new RuntimeException(error))

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

      mailer.serverExceptionNotifyInternal(
        s"FAILED Received by $phata-${apiEndpointVariant.endpoint.name}-${apiEndpointVariant.variant}: $error",
        new RuntimeException(error))

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
    wsClient: WSClient,
    dataplugEndpointService: DataPlugEndpointService,
    mailer: Mailer,
    executionContext: ExecutionContext): Props =
    Props(new PhataDataPlugVariantSyncer(phata, endpointInterface, apiEndpointVariant,
      configuration, hatClientFactory, wsClient, dataplugEndpointService, mailer, executionContext))
}