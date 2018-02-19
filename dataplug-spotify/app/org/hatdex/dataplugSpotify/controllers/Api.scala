/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugSpotify.controllers

import javax.inject.Inject

import akka.actor.ActorSystem
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager, SubscriptionEventBus, UserService }
import org.hatdex.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.Configuration

import scala.concurrent.ExecutionContext

class Api @Inject() (
    components: ControllerComponents,
    configuration: Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction,
    dataPlugEndpointService: DataPlugEndpointService,
    wsClient: WSClient,
    userService: UserService,
    socialProviderRegistry: SocialProviderRegistry,
    subscriptionEventBus: SubscriptionEventBus,
    actorSystem: ActorSystem,
    syncerActorManager: DataplugSyncerActorManager) extends AbstractController(components) {

  val ioEC: ExecutionContext = IoExecutionContext.ioThreadPool
}

