/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.utils

import javax.inject.Inject

import com.nimbusds.jwt.SignedJWT
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.services.UserService
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.Future
import scala.util.Try

class JwtPhataAuthenticatedRequest[A](val identity: User, val request: Request[A])
  extends WrappedRequest[A](request)

class JwtPhataAuthenticatedAction @Inject() (
    identityVerification: JwtIdentityVerification,
    configuration: play.api.Configuration,
    userService: UserService) extends ActionBuilder[JwtPhataAuthenticatedRequest] {

  val logger = Logger("JwtPhataAuthentication")
  def invokeBlock[A](request: Request[A], block: (JwtPhataAuthenticatedRequest[A]) => Future[Result]): Future[Result] = {
    request.headers.get("X-Auth-Token")
      .orElse(request.getQueryString("X-Auth-Token"))
      .orElse(request.getQueryString("token"))
      .map(validateJwtToken)
      .map { eventualMaybeUser =>
        eventualMaybeUser.flatMap { maybeIdentity =>
          maybeIdentity map { identity =>
            userService.save(identity)
              .flatMap(_ => userService.retrieve(identity.loginInfo).map(_.get)) // must have a user when we've just inserted one
              .flatMap(saved => block(new JwtPhataAuthenticatedRequest(saved, request)))
          } getOrElse {
            Future.successful(Results.Unauthorized)
          }

        } recover {
          case e =>
            logger.error(s"Error while authenticating: ${e.getMessage}")
            Results.Unauthorized(Json.obj("status" -> "unauthorized", "message" -> s"No valid login information"))
        }
      }
      .getOrElse(Future.successful(Results.Unauthorized))
  }

  def validateJwtToken(token: String): Future[Option[User]] = {
    val expectedResources = configuration.getStringSeq("auth.allowedResources").get
    val expectedAccessCope = "validate"
    val maybeSignedJWT = Try(SignedJWT.parse(token))

    maybeSignedJWT.map { signedJWT =>
      val claimSet = signedJWT.getJWTClaimsSet
      val fresh = claimSet.getExpirationTime.after(DateTime.now().toDate)
      val resourceMatches = Option(claimSet.getClaim("resource").asInstanceOf[String]).map { resource =>
        expectedResources.exists(er => resource.startsWith(er))
      } getOrElse {
        false
      }
      val accessScopeMatches = Option(claimSet.getClaim("accessScope")).contains(expectedAccessCope)

      if (fresh && resourceMatches && accessScopeMatches) {
        val identity = User("hatlogin", claimSet.getIssuer, List())
        identityVerification.verifiedIdentity(identity, signedJWT)
      }
      else {
        logger.debug(s"JWT token validation failed: fresh - $fresh, resource - $resourceMatches, scope - $accessScopeMatches")
        Future(None)
      }
    } getOrElse {
      // JWT parse error
      Future(None)
    }
  }
}

class JwtPhataAwareRequest[A](val maybeUser: Option[User], val request: Request[A])
  extends WrappedRequest[A](request)

class JwtPhataAwareAction @Inject() (
    identityVerification: JwtIdentityVerification,
    configuration: play.api.Configuration,
    userService: UserService,
    jwtAuthenticatedAction: JwtPhataAuthenticatedAction) extends ActionBuilder[JwtPhataAwareRequest] {

  val logger = Logger("JwtPhataAuthentication")

  def invokeBlock[A](request: Request[A], block: (JwtPhataAwareRequest[A]) => Future[Result]): Future[Result] = {
    request.headers.get("X-Auth-Token")
      .orElse(request.getQueryString("X-Auth-Token"))
      .orElse(request.getQueryString("token"))
      .map(jwtAuthenticatedAction.validateJwtToken)
      .map { eventualMaybeUser =>
        eventualMaybeUser.flatMap { maybe =>
          logger.debug(s"User auth checked, got back user $maybe")
          val eventuallySavedUser = maybe map { identity =>
            userService.save(identity)
              .flatMap(_ => userService.retrieve(identity.loginInfo)) // must have a user when we've just inserted one
          } getOrElse {
            Future.successful(None)
          }
          eventuallySavedUser flatMap { maybeUser =>
            block(new JwtPhataAwareRequest(maybeUser, request))
          }
        } recoverWith {
          case e =>
            logger.error(s"Error while authenticating: ${e.getMessage}")
            block(new JwtPhataAwareRequest(None, request))
        }
      }
      .getOrElse(block(new JwtPhataAwareRequest(None, request)))
  }
}
