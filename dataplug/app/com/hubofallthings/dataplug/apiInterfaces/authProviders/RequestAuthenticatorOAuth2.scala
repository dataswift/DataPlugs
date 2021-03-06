/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.apiInterfaces.authProviders

import com.hubofallthings.dataplug.apiInterfaces.models.ApiEndpointCall
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.{ OAuth2Info, OAuth2Provider }
import com.hubofallthings.dataplug.actors.Errors.{ HATAuthenticationException, SourceAuthenticationException }
import com.hubofallthings.dataplug.services.UserService
import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

trait RequestAuthenticatorOAuth2 extends RequestAuthenticator {
  type AuthInfoType = OAuth2Info

  protected val userService: UserService
  protected val authInfoRepository: AuthInfoRepository
  protected val tokenHelper: OAuth2TokenHelper
  protected val provider: OAuth2Provider
  protected val logger: Logger

  def authenticateRequest(params: ApiEndpointCall, hatAddress: String, refreshToken: Boolean = true)(implicit ec: ExecutionContext): Future[ApiEndpointCall] = {
    val existingAuthInfo = userService.retrieve(LoginInfo("hatlogin", hatAddress))
      .map(_.getOrElse(throw HATAuthenticationException("Required HAT user not found")))
      .flatMap { user =>
        logger.debug(s"Found user for $hatAddress: $user")
        user.linkedUsers.find(_.providerId == provider.id)
          .map { providerLoginInfo =>
            authInfoRepository.find[AuthInfoType](providerLoginInfo.loginInfo)
              .map(li => (user, providerLoginInfo, li.get))
          } getOrElse {
            Future.failed(SourceAuthenticationException("Required source authentication information not found"))
          }
      }

    val eventualAuthInfo = existingAuthInfo flatMap {
      case (user, providerUser, authInfo) =>
        // Refresh token if refreshToken is available, otherwise try using what we have
        authInfo.refreshToken flatMap { token =>
          if (refreshToken) {
            logger.debug(s"Got refresh token, refreshing (retrying? $refreshToken)")
            tokenHelper.refresh(providerUser.loginInfo, token)
              .map(
                _.andThen { // Return the token and then
                  // If the access token has changed from the last known value, save that in the repository
                  case Success(refreshedToken) if refreshedToken.accessToken != authInfo.accessToken =>
                    logger.debug(s"OAuth info refreshed on ${params.path} endpoint call. New values being saved for $hatAddress: $refreshedToken")
                    val providerLoginInfo = user.linkedUsers.find(_.providerId == provider.id).get
                    authInfoRepository.save[AuthInfoType](providerLoginInfo.loginInfo, refreshedToken)

                  case e => logger.warn(s"Token refresh was not successful. Reason: $e")
                })
          }
          else {
            Some(Future.successful(authInfo))
          }
        } getOrElse {
          logger.debug("No refresh token, trying what we have")
          Future.successful(authInfo)
        }
      //        Future.successful(authInfo)
    }

    eventualAuthInfo map { authInfo =>
      attachAccessToken(params, authInfo)
    }
  }

  def attachAccessToken(params: ApiEndpointCall, authInfo: OAuth2Info): ApiEndpointCall = {
    logger.debug(s"token info: ${authInfo}")
    params.copy(headers = params.headers + ("Authorization" -> s"Bearer ${authInfo.accessToken}"))
  }
}

