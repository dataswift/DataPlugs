/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.services

import javax.inject.{ Inject, Named }
import akka.Done
import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging
import akka.stream.scaladsl.Source
import akka.stream.{ Materializer, ThrottleMode }
import com.mohiva.play.silhouette.impl.providers.{ SocialProvider, SocialProviderRegistry }
import org.hatdex.dataplug.actors.DataPlugManagerActor.{ DataPlugManagerActorMessage, Start, Stop }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointVariant, ApiEndpointVariantChoice }
import org.hatdex.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry }
import org.hatdex.dataplug.models.User
import org.joda.time.DateTime
import play.api.Logger

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class DataplugSyncerActorManager @Inject() (
    socialProviderRegistry: SocialProviderRegistry,
    dataPlugEndpointService: DataPlugEndpointService,
    optionsCollectionRegistry: DataPlugOptionsCollectorRegistry,
    subscriptionEventBus: SubscriptionEventBus,
    actorSystem: ActorSystem,
    @Named("dataPlugManager") dataPlugManagerActor: ActorRef)(implicit val materializer: Materializer) {

  private val logger = Logger(this.getClass)
  private val streamLogger = Logging(actorSystem, this.getClass)

  def updateApiVariantChoices(user: User, variantChoices: Seq[ApiEndpointVariantChoice])(implicit ec: ExecutionContext): Future[Unit] = {
    dataPlugEndpointService.updateApiVariantChoices(user.userId, variantChoices) map { _ =>
      variantChoices foreach { variantChoice =>
        val message: DataPlugManagerActorMessage = if (variantChoice.active) {
          subscriptionEventBus.publish(SubscriptionEventBus.UserSubscribedEvent(user, DateTime.now(), variantChoice))

          // TODO: obtain HAT access token to pass into actor Start message
          Start(variantChoice.variant, user.userId, "", variantChoice.variant.configuration)
        }
        else {
          subscriptionEventBus.publish(SubscriptionEventBus.UserUnsubscribedEvent(user, DateTime.now(), variantChoice))
          Stop(variantChoice.variant, user.userId)
        }
        logger.debug(s"For variant $variantChoice sending message $message to manager")
        dataPlugManagerActor ! message
      }
    }
  }

  def startAllActiveVariantChoices()(implicit ec: ExecutionContext): Future[Done] = {
    dataPlugEndpointService.retrieveAllActiveEndpointsStream
      .log("Initializing endpoint synchronisation")(streamLogger)
      .throttle(1, 30.seconds, 1, ThrottleMode.Shaping)
      .map {
        case (phata: String, accessToken: String, variant: ApiEndpointVariant) =>
          dataPlugManagerActor ! Start(variant, phata, accessToken, variant.configuration)
          phata
      }
      .runForeach(r => logger.info(s"Synchronisation started for $r"))
      .recoverWith {
        case e =>
          logger.error(s"Could not retrieve endpoints to sync: ${e.getMessage}")
          Future.failed(e)
      }
  }

  def runPhataActiveVariantChoices(phata: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Starting active API endpoint syncing")
    dataPlugEndpointService.retrievePhataEndpoints(phata) map { phataVariants =>
      logger.info(s"Retrieved endpoints to sync: ${phataVariants.mkString("\n")}")
      phataVariants foreach { variant =>
        // TODO: obtain HAT access token to pass into actor Start message
        dataPlugManagerActor ! Start(variant, phata, "", variant.configuration)
      }
    } recoverWith {
      case e =>
        logger.error(s"Could not retrieve endpoints to sync: ${e.getMessage}")
        Future.failed(e)
    }
  }

  def currentProviderApiVariantChoices(user: User, providerName: String)(implicit executionContext: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    val socialProvider = socialProviderRegistry.get[SocialProvider](providerName)

    val optionsCollectors = socialProvider.map { provider =>
      optionsCollectionRegistry.getSeqProvider[provider.type, DataPlugOptionsCollector]
    } getOrElse Seq()

    val optionsLists = optionsCollectors.map { collector =>
      for {
        apiCall <- collector.buildFetchParameters(None)
        choices <- collector.get(apiCall, user.userId, None.orNull, retrying = false) // Odd choice of using null here due to common parent interface, but Hat Actor not being necessary
        enabledVariants <- dataPlugEndpointService.enabledApiVariantChoices(user.userId)
      } yield {
        choices.map { choice =>
          choice.copy(
            active = enabledVariants.exists(v => v.key == choice.key && v.variant.variant == choice.variant.variant),
            variant = enabledVariants.find(_.key == choice.key).map(_.variant).getOrElse(choice.variant))
        }
      }
    }

    Future.sequence(optionsLists).map(_.flatten)
  }

  def currentProviderStaticApiVariantChoices(userId: String, providerName: String)(implicit executionContext: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    val socialProvider = socialProviderRegistry.get[SocialProvider](providerName)

    val optionsCollectors = socialProvider.map { provider =>
      optionsCollectionRegistry.getSeqProvider[provider.type, DataPlugOptionsCollector]
    } getOrElse Seq()

    val optionsLists = optionsCollectors.map { collector =>
      for {
        choices <- Future.successful(collector.generateEndpointChoices(None))
        enabledVariants <- dataPlugEndpointService.enabledApiVariantChoices(userId)
      } yield {
        choices.map { choice =>
          choice.copy(
            active = enabledVariants.exists(v => v.key == choice.key && v.variant.variant == choice.variant.variant),
            variant = enabledVariants.find(_.key == choice.key).map(_.variant).getOrElse(choice.variant))
        }
      }
    }

    Future.sequence(optionsLists).map(_.flatten)
  }

}
