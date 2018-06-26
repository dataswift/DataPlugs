package org.hatdex.dataplug.utils

import org.hatdex.hat.api.models.EndpointData
import org.hatdex.hat.api.services.HatClient
import play.api.Logger
import play.api.libs.json.JsArray
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class AuthenticatedHatClient(ws: WSClient, hat: String, protocol: String, accessToken: String)(implicit ec: ExecutionContext) {
  protected val logger = Logger(this.getClass)
  protected val apiVersion = "v2.6"

  protected val hatClient: HatClient = new HatClient(ws, hat, protocol, apiVersion)

  def postData(namespace: String, endpoint: String, data: JsArray): Future[Seq[EndpointData]] =
    hatClient.saveData(accessToken, namespace, endpoint, data, skipErrors = true)
}
