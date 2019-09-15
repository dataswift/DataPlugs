/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplugFacebook.modules

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ Materializer, OverflowStrategy, ThrottleMode }
import com.google.inject.{ AbstractModule, Provides }
import com.hubofallthings.dataplug.actors.{ DataPlugManagerActor, ForwardingActor }
import javax.inject.Named
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.duration._

class FacebookActorsModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  def configure: Unit = {
    bindActor[DataPlugManagerActor]("dataPlugManager")
    bindActor[ForwardingActor]("syncDispatcher")
  }

  @Provides @Named("syncThrottler")
  def provideDataPlugCollection(@Named("syncDispatcher") syncDispatcher: ActorRef)(implicit materializer: Materializer): ActorRef = {
    Source.actorRef[DataPlugManagerActor.DataPlugSyncDispatcherActorMessage](bufferSize = 1000, OverflowStrategy.dropNew)
      .throttle(elements = 4, per = 1.second, maximumBurst = 10, ThrottleMode.Shaping)
      .to(Sink.actorRef(syncDispatcher, NotUsed))
      .run()
  }
}
