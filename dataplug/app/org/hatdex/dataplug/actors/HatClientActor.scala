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

  case class PostData(namespace: String, endpoint: String, data: JsArray)
  case class DataSaved(data: Seq[EndpointData])
  case class ReqFailed(message: String, cause: ApiException)
}

class HatClientActor(ws: WSClient, protocol: String, credentials: HatAccessCredentials) extends Actor {
  import HatClientActor._
  protected val logger = Logger(this.getClass)

  // Use the actor's dispatcher as execution context for api calls
  protected implicit val ec: ExecutionContext = context.dispatcher

  protected val hatClient: HatClient = new HatClient(ws, credentials.hat, protocol)
  protected val token: String = credentials.accessToken

  def receive: Receive = {
    case PostData(namespace, endpoint, data) =>
      val savedData = hatClient.saveData(token, namespace, endpoint, data)
        .map(d => DataSaved(d))
        .recover {
          case e: ApiException =>
            val message = s"Could not post data values to ${credentials.hat}: ${e.getMessage}"
            logger.error(message)
            ReqFailed(message, e)
        }
      savedData pipeTo sender
  }
}
