/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.controllers

import javax.inject.{ Inject, Named }

import akka.actor.{ ActorRef, ActorSystem }
import org.hatdex.dataplug.utils.{ JwtPhataAuthenticatedAction, JwtPhataAwareAction }
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.json.{ JsError, Json }
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Api @Inject() (
    messagesApi: MessagesApi,
    configuration: play.api.Configuration,
    tokenUserAwareAction: JwtPhataAwareAction,
    tokenUserAuthenticatedAction: JwtPhataAuthenticatedAction) extends Controller {

  def tickle(hat: String): Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    Future.successful(InternalServerError(Json.toJson(Map("message" -> "Not Implemented", "error" -> "Not implemented"))))
  }

  def status(hat: String): Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    Future.successful(InternalServerError(Json.toJson(Map("message" -> "Not Implemented", "error" -> "Not implemented"))))
  }

  def permissions(hat: String): Action[AnyContent] = tokenUserAuthenticatedAction.async { implicit request =>
    Future.successful(InternalServerError(Json.toJson(Map("message" -> "Not Implemented", "error" -> "Not implemented"))))
  }

}
