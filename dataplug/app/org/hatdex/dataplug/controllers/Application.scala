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
import org.hatdex.dataplug.utils.JwtPhataAwareAction
import org.hatdex.dataplug.views
import play.api.i18n.MessagesApi
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class Application @Inject() (
    messagesApi: MessagesApi,
    configuration: play.api.Configuration,
    wsClient: WSClient,
    actorSystem: ActorSystem,
    tokenUserAwareAction: JwtPhataAwareAction) extends Controller {

  def index(): Action[AnyContent] = tokenUserAwareAction.async { implicit request =>
    Future.successful(InternalServerError("Not implemented"))
  }
}

