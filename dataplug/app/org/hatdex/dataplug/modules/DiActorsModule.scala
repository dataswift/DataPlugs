/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.modules

import javax.inject.Named

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ Materializer, OverflowStrategy, ThrottleMode }
import com.google.inject.{ AbstractModule, Provides }
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.dataplug.actors.{ DataPlugManagerActor, ForwardingActor }
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.duration._

class DiActorsModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  def configure = {
    bindActor[DataPlugManagerActor]("dataPlugManager")
    bindActor[ForwardingActor]("syncDispatcher")
    bind[org.hatdex.commonPlay.utils.Mailer].to[org.hatdex.dataplug.utils.Mailer]
  }

  @Provides @Named("syncThrottler")
  def provideDataPlugCollection(@Named("syncDispatcher") syncDispatcher: ActorRef)(implicit materializer: Materializer): ActorRef = {
    Source.actorRef[DataPlugManagerActor.DataPlugSyncDispatcherActorMessage](bufferSize = 1000, OverflowStrategy.dropNew)
      .throttle(elements = 1, per = 1.second, maximumBurst = 5, ThrottleMode.Shaping)
      .to(Sink.actorRef(syncDispatcher, NotUsed))
      .run()
  }

}
