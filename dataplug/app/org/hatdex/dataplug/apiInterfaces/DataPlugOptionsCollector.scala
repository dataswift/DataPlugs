package org.hatdex.dataplug.apiInterfaces

import akka.actor.ActorRef
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticator
import org.hatdex.dataplug.apiInterfaces.models._

import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugOptionsCollector extends RequestAuthenticator with DataPlugApiEndpointClient {
  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]]
}
