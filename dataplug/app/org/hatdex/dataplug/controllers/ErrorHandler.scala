/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.controllers

import javax.inject._

import com.mohiva.play.silhouette.api.actions.{ SecuredErrorHandler, UnsecuredErrorHandler }
import play.api._
import play.api.http.{ ContentTypes, DefaultHttpErrorHandler }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router

import scala.concurrent.Future

class ErrorHandler @Inject() (
    env: Environment,
    config: Configuration,
    sourceMapper: OptionalSourceMapper,
    router: Provider[Router],
    val messagesApi: MessagesApi) extends DefaultHttpErrorHandler(env, config, sourceMapper, router)
  with SecuredErrorHandler with UnsecuredErrorHandler with I18nSupport with ContentTypes with RequestExtractors with Rendering {

  // 401 - Unauthorized
  override def onNotAuthenticated(implicit request: RequestHeader): Future[Result] = {
    Logger.debug("[Silhouette] Not authenticated")
    Future.successful {
      render {
        case Accepts.Json() => Unauthorized(Json.obj("error" -> "Not Authenticated", "message" -> s"Not Authenticated"))
        case _              => Redirect(routes.Application.signIn()).flashing("signinRedirect" -> Messages("auth.signin.redirect", request.path + request.rawQueryString))
      }
    }
  }

  // 403 - Forbidden
  override def onNotAuthorized(implicit request: RequestHeader): Future[Result] = {
    Future.successful {
      render {
        case Accepts.Json() => Forbidden(Json.obj("error" -> "Forbidden", "message" -> s"Access Denied"))
        case _              => Redirect(routes.Application.signIn())
      }
    }
  }

  /**
   * Invoked when a client makes a request that was forbidden.
   *
   * @param request The forbidden request.
   * @param message The error message.
   */
  override protected def onForbidden(request: RequestHeader, message: String): Future[Result] = {
    Logger.error(s"On forbidden: $message")
    Future.successful(Redirect(routes.Application.signIn()))
  }

  //  // 404 - page not found error
  //  override def onNotFound(request: RequestHeader, message: String): Future[Result] = Future.successful {
  //    implicit val _request = request
  //    implicit val noUser: Option[User] = None
  //    NotFound(env.mode match {
  //      case Mode.Prod => views.html.notFound(request.path)
  //      case _         => views.html.defaultpages.devNotFound(request.method, request.uri, Some(router.get))
  //    })
  //  }
  //
  //  // 500 - internal server error
  //  override def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] = {
  //    implicit val _request = request
  //    implicit val noUser: Option[User] = None
  //    mailer.serverErrorNotify(request, exception)
  //    Future.successful {
  //      render {
  //        case Accepts.Json() =>
  //          InternalServerError(Json.obj(
  //            "error" -> "Internal Server error",
  //            "message" -> s"A server error occurred, please report this error code to our admins: ${exception.id}"))
  //        case _ =>
  //          InternalServerError(views.html.internalServerError(s"A server error occurred, please report this error code to our admins: ${exception.id}"))
  //      }
  //    }
  //  }
  //
  //  override def onBadRequest(request: RequestHeader, message: String): Future[Result] = {
  //    implicit val _request = request
  //    implicit val noUser: Option[User] = None
  //    Future.successful {
  //      render {
  //        case Accepts.Json() => BadRequest(Json.obj("error" -> "Bad Request", "message" -> message))
  //        case _              => BadRequest(views.html.badRequest(message))
  //      }
  //    }
  //  }
}