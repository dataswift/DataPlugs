package org.hatdex.dataplug.actors

import javax.inject.Inject

import akka.actor.{ Actor, _ }
import com.google.inject.assistedinject.Assisted
import com.typesafe.config.Config
import org.hatdex.dataplug.models.HatClientCredentials
import play.api.libs.ws.WSClient

class InjectedHatClientActor @Inject() (
    wsClient: WSClient,
    @Assisted hat: String,
    @Assisted config: Config,
    @Assisted credentials: HatClientCredentials) extends HatClientActor(wsClient, hat, config, credentials) with Stash {

}

object InjectedHatClientActor {

  trait Factory {
    def apply(hat: String, config: Config, credentials: HatClientCredentials): Actor
  }

}