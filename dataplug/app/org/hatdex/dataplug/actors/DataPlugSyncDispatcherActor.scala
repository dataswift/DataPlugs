/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 5, 2017
 */

package org.hatdex.dataplug.actors

import javax.inject.Inject

import akka.actor.{ Actor, ActorLogging, ActorPath }
import org.hatdex.dataplug.actors.DataPlugManagerActor.Fetch
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointCall
import org.hatdex.dataplug.utils.Mailer

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object DataPlugSyncDispatcherActor {
  sealed trait DataPlugSyncDispatcherActorMessage

  case class Sync(
    actorPath: ActorPath,
    fetchEndpointCall: Option[ApiEndpointCall]) extends DataPlugSyncDispatcherActorMessage
}

class DataPlugSyncDispatcherActor @Inject() (
    val mailer: Mailer) extends Actor with ActorLogging {

  import DataPlugSyncDispatcherActor._

  var requestsIssued: Long = 0L

  implicit val executionContext: ExecutionContext = context.dispatcher

  def receive: Receive = {
    case Sync(actorPath, fetchEndpointCall) =>
      context.actorSelection(actorPath).resolveOne(5.seconds) map { syncActor =>
        log.info(s"[$requestsIssued] Starting sync actor $syncActor")
        requestsIssued = requestsIssued + 1L
        syncActor ! Fetch(fetchEndpointCall, 0)
      } recover {
        case e =>
          val message = s"Could not fetch for actor $actorPath - ${e.getMessage}"
          log.error(message)
          mailer.serverExceptionNotifyInternal(message, new RuntimeException(message))
      }
  }
}
