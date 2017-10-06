/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2017
 */

package org.hatdex.dataplug.actors

import akka.actor.{ ActorContext, ActorNotFound, ActorRef, Props }
import play.api.Logger

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait RetryingActorLauncher {
  protected val context: ActorContext
  protected val maxAttempts = 3
  protected val logger: Logger

  protected def launchActor(actorProps: Props, selection: String, depth: Int = 0, timeout: FiniteDuration = 1.second)(implicit ec: ExecutionContext): Future[ActorRef] = {
    if (depth >= maxAttempts) {
      logger.warn(s"Actor for $selection not resolved")
      throw new RuntimeException(s"Can not create actor for $selection and reached max attempts of $maxAttempts")
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
