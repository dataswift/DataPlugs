/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugSpotify.controllers

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
}

