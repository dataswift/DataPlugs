/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.controllers

import javax.inject.{ Inject, Named }

import akka.actor.ActorRef
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.commonPlay.models.auth.forms.AuthForms
import org.hatdex.dataplug.actors.DataPlugManagerActor.{ Start, Stop }
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointVariantChoice
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.services.{ DataPlugEndpointService, DataplugSyncerActorManager }
import org.hatdex.dataplug.utils.{ PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

class Application @Inject() (
    val messagesApi: MessagesApi,
    configuration: play.api.Configuration,
    socialProviderRegistry: SocialProviderRegistry,
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    dataPlugViewSet: DataPlugViewSet,
    syncerActorManager: DataplugSyncerActorManager,
    clock: Clock) extends SilhouettePhataAuthenticationController(silhouette, clock, configuration) {

  def index(): Action[AnyContent] = UserAwareAction.async { implicit request =>
    Logger.debug(s"Maybe user? ${request.identity}")
    request.identity.map { implicit user =>
      Future.successful(Ok(dataPlugViewSet.connect(socialProviderRegistry, None, dataPlugViewSet.variantsForm)))
    } getOrElse {
      Future.successful(Ok(dataPlugViewSet.signIn(AuthForms.signinHatForm)))
    }
  }

}

