/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.apiInterfaces.authProviders

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.{ OAuth2Info, OAuth2Provider }
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointCall
import org.hatdex.dataplug.services.UserService
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
    val eventualUser = userService.retrieve(LoginInfo("hatlogin", hatAddress)).map(_.get)
    val existingAuthInfo = eventualUser flatMap { user =>
      val providerLoginInfo = user.linkedUsers.find(_.providerId == provider.id).get
      authInfoRepository.find[AuthInfoType](providerLoginInfo.loginInfo).map(li => (providerLoginInfo, li.get))
    }

    val eventualAuthInfo = existingAuthInfo flatMap {
      case (providerUser, authInfo) =>
        // Refresh token if refreshToken is available, otherwise try using what we have
        authInfo.refreshToken flatMap { token =>
          if (refreshToken) {
            logger.debug("Got refresh token, refreshing")
            tokenHelper.refresh(providerUser.loginInfo, token)
              .map(eventualToken => eventualToken.andThen {
                // If the access token has changed from the last known value, save that in the repository
                case Success(refreshedToken) if refreshedToken.accessToken != authInfo.accessToken =>
                  eventualUser flatMap { user =>
                    val providerLoginInfo = user.linkedUsers.find(_.providerId == provider.id).get
                    authInfoRepository.save[AuthInfoType](providerLoginInfo.loginInfo, refreshedToken)
                  }
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
      params.copy(headers = Map("Authorization" -> s"Bearer ${authInfo.accessToken}"))
    }
  }
}

