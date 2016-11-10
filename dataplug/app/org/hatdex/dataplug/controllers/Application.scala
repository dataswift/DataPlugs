/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.controllers

import java.util.UUID
import javax.inject.{ Inject, Named }

import akka.actor.{ ActorRef, ActorSystem }
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.hatdex.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction, PhataAuthenticationEnvironment, SilhouettePhataAuthenticationController }
import org.hatdex.dataplug.views
import play.api.i18n.MessagesApi
import play.api.libs.ws.WSClient
import play.api.mvc._
import org.hatdex.dataplug.views

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import org.hatdex.commonPlay.models.auth.forms.AuthForms
import org.hatdex.commonPlay.utils.MailService
import play.api.Logger

import scala.util.Try

class Application @Inject() (
    val messagesApi: MessagesApi,
    configuration: play.api.Configuration,
    socialProviderRegistry: SocialProviderRegistry,
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    clock: Clock) extends SilhouettePhataAuthenticationController(silhouette, clock, configuration) {

  def index(): Action[AnyContent] = UserAwareAction.async { implicit request =>
    Logger.debug(s"Maybe user? ${request.identity}")
    request.identity.map { implicit user =>
      Future.successful(Ok(views.html.connect(socialProviderRegistry)))
    } getOrElse {
      Future.successful(Ok(views.html.signIn(AuthForms.signinHatForm)))
    }
  }

}

