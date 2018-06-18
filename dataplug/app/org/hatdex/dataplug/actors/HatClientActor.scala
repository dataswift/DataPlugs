package org.hatdex.dataplug.actors

import akka.actor.{ Actor, Props }
import akka.pattern.pipe
import org.hatdex.dataplug.models.HatAccessCredentials
import org.hatdex.hat.api.models.EndpointData
import org.hatdex.hat.api.services.Errors.ApiException
import org.hatdex.hat.api.services.HatClient
import play.api.Logger
import play.api.libs.json.JsArray
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

object HatClientActor {
  def props(ws: WSClient, protocol: String, credentials: HatAccessCredentials): Props =
    Props(new HatClientActor(ws, protocol, credentials))

  sealed trait HatClientActorMessage
  case class PostData(namespace: String, endpoint: String, data: JsArray) extends HatClientActorMessage
  case class DataSaved(data: Seq[EndpointData]) extends HatClientActorMessage
  case class ReqFailed(message: String, cause: ApiException) extends HatClientActorMessage
}

class HatClientActor(ws: WSClient, protocol: String, credentials: HatAccessCredentials) extends Actor {
  import HatClientActor._
  protected val logger = Logger(this.getClass)

  // Use the actor's dispatcher as execution context for api calls
  protected implicit val ec: ExecutionContext = context.dispatcher
  protected val apiVersion = "v2.6"

  protected val hatClient: HatClient = new HatClient(ws, credentials.hat, protocol, apiVersion)
  protected val token: String = credentials.accessToken

  def receive: Receive = {
    case PostData(namespace, endpoint, data) =>
      val savedData = hatClient.saveData(token, namespace, endpoint, data, skipErrors = true)
        .map(d => {
          logger.debug(s"Posted new records to ${credentials.hat}")
          DataSaved(d)
        })
        .recover {
          case e: ApiException =>
            val message = s"Could not post data values to ${credentials.hat}: ${e.getMessage}"
            logger.error(message)
            ReqFailed(message, e)

          case e =>
            val message = s"Unrecognised HAT response from ${credentials.hat}: $e"
            logger.error(message)
        }
      savedData pipeTo sender

    case m => logger.error(s"Unrecognized message $m")
  }
}
