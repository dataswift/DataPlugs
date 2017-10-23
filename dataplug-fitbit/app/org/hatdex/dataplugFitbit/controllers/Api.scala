/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugFitbit.controllers

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

import akka.actor.{ Actor, ActorSystem, Props }
import com.google.common.io.BaseEncoding
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.{ OAuth2Provider, SocialProviderRegistry }
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager, SubscriptionEventBus, UserService }
import org.hatdex.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import org.hatdex.dataplugFitbit.apiInterfaces.FitbitSubscription
import play.api.i18n.MessagesApi
import play.api.libs.json.{ Format, Json }
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{ Configuration, Logger }

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class Api @Inject() (
    messagesApi: MessagesApi,
    configuration: Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction,
    dataPlugEndpointService: DataPlugEndpointService,
    wsClient: WSClient,
    userService: UserService,
    socialProviderRegistry: SocialProviderRegistry,
    subscriptionEventBus: SubscriptionEventBus,
    actorSystem: ActorSystem,
    fitbitSubscription: FitbitSubscription,
    syncerActorManager: DataplugSyncerActorManager) extends Controller {

  private val logger = Logger(this.getClass)

  val ioEC: ExecutionContext = IoExecutionContext.ioThreadPool

  private val verificationCode = "07aea184292ab16cdd1f1912956e3dbe9087129635f6fdb7c977bc4d63d12652"
  private val provider: String = configuration.getString("service.name").getOrElse("").toLowerCase
  private val oauth2Provider = socialProviderRegistry.get[OAuth2Provider](provider).get

  def webhookVerify(verify: String): EssentialAction = Action {
    if (verify == verificationCode) {
      NoContent
    }
    else {
      NotFound
    }
  }

  implicit val notificationFormat: Format[FitbitActivityNotification] = Json.format[FitbitActivityNotification]
  def handleNotification(): Action[AnyContent] = Action { implicit request =>
    request.body.asText map { textContent =>
      val signature = generateSignature(textContent, oauth2Provider.ClientSecret)
      val headerSignature = request.headers.get("X-Fitbit-Signature")
      if (headerSignature.contains(signature)) {
        Json.parse(textContent).asOpt[FitbitActivityNotification] map { notification =>
          userService.retrieve(LoginInfo(provider, notification.ownerId)) map { user =>
            logger.info(s"Got activity notification for $user: $notification")
          }
        } getOrElse {
          logger.error("Failed to parse content to notifications")
        }
        NoContent
      }
      else {
        logger.warn(s"Signatures did not match: $signature != $headerSignature")
        logger.warn(s"Attempted notification was: ${request.body}")
        NotFound
      }
    } getOrElse {
      NotFound
    }
  }

  protected def generateSignature(content: String, secretKey: String): String = {
    val keyspec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA512")
    val shaMac = Mac.getInstance("HmacSHA512")
    shaMac.init(keyspec)

    val macData = shaMac.doFinal(content.getBytes())
    BaseEncoding.base64().encode(macData)
  }

  subscriptionEventBus.subscribe(
    actorSystem.actorOf(SubscriptionManagerActor.props(fitbitSubscription)),
    classOf[SubscriptionEventBus.SubscriptionEvent])

}

case class FitbitActivityNotification(
    collectionType: String,
    date: String,
    ownerId: String,
    ownerType: String,
    subscriptionId: String)

object SubscriptionManagerActor {
  def props(fitbitSubscription: FitbitSubscription): Props = Props(new SubscriptionManagerActor(fitbitSubscription))
}

class SubscriptionManagerActor(fitbitSubscription: FitbitSubscription) extends Actor {
  def receive: Receive = {
    case SubscriptionEventBus.UserSubscribedEvent(user, _, variantChoice) =>
      variantChoiceToCollectionKey(variantChoice)
        .map(fitbitSubscription.create(_, user.userId))

    case SubscriptionEventBus.UserUnsubscribedEvent(user, _, variantChoice) =>
      variantChoiceToCollectionKey(variantChoice)
        .map(fitbitSubscription.delete(_, user.userId))
  }

  protected def variantChoiceToCollectionKey(variantChoice: ApiEndpointVariantChoice): Option[String] = {
    variantChoice.key match {
      case "activity" => Some("activities")
      case "weight"   => Some("body")
      case "sleep"    => Some("sleep")
      case _          => None
    }
  }
}