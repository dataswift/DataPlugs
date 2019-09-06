/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 5, 2017
 */

package com.hubofallthings.dataplug.actors

import java.util.concurrent.Executors

import akka.actor.{ Actor, ActorContext, ActorNotFound, ActorRef, Props }
import Errors.DataPlugError
import play.api.Logger

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object IoExecutionContext {
  private val concurrency = Runtime.getRuntime.availableProcessors()
  private val factor = 5 // get from configuration  file
  private val noOfThread = concurrency * factor
  implicit val ioThreadPool: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(noOfThread))
}

class ForwardingActor extends Actor {

  protected val logger: Logger = Logger(this.getClass)

  def receive: Receive = {
    case DataPlugManagerActor.Forward(message, to) =>
      logger.debug(s"Forwarding $message to ${to.path}")
      to.forward(message)
  }
}

trait RetryingActorLauncher {
  protected val context: ActorContext
  protected val maxAttempts = 3
  protected val logger: Logger

  protected def launchActor(actorProps: Props, selection: String, depth: Int = 0, timeout: FiniteDuration = 1.second)(implicit ec: ExecutionContext): Future[ActorRef] = {
    if (depth >= maxAttempts) {
      logger.warn(s"Actor for $selection not resolved")
      throw new DataPlugError(s"Can not create actor for $selection and reached max attempts of $maxAttempts")
    }

    context.actorSelection(s"${context.self.path}/$selection").resolveOne(timeout) map { clientActor =>
      logger.debug(s"Actor $selection resolved")
      clientActor
    } recoverWith {
      case ActorNotFound(actorSelection) =>
        logger.debug(s"Actor $actorSelection not found, creating")

        Try(context.actorOf(actorProps, selection))
        launchActor(actorProps, selection, depth + 1, timeout)
    }
  }
}
