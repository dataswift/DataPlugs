/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.apiInterfaces.authProviders

import java.net.URLEncoder

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth1.services.PlayOAuth1Service
import com.mohiva.play.silhouette.impl.providers.{ OAuth1Info, OAuth1Provider, OAuth2Info, OAuth2Provider }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import play.api.Logger
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }

trait RequestAuthenticatorOAuth1 extends RequestAuthenticator {
  type AuthInfoType = OAuth1Info

  protected val userService: UserService
  protected val authInfoRepository: AuthInfoRepository
  protected val provider: OAuth1Provider
  protected val logger: Logger
  protected val wsClient: WSClient

  lazy val oauth1service = new PlayOAuth1Service(provider.settings)

  def authenticateRequest(params: ApiEndpointCall, hatAddress: String)(implicit ec: ExecutionContext): Future[ApiEndpointCall] = {
    Future.successful(params.copy(pathParameters = params.pathParameters ++ Map("hatAddress" -> hatAddress)))
  }

  protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] = {
    logger.debug(s"Building api request for $params")
    val eventualUser = userService.retrieve(LoginInfo("hatlogin", params.pathParameters("hatAddress"))).map(_.get)
    val eventualAuthInfo = eventualUser flatMap { user =>
      val providerLoginInfo = user.linkedUsers.find(_.providerId == provider.id).get
      authInfoRepository.find[AuthInfoType](providerLoginInfo.loginInfo).map(_.get)
    }

    val path = params.pathParameters.foldLeft(params.path) { (path, parameter) =>
      path.replace(s"[${parameter._1}]", URLEncoder.encode(parameter._2, "UTF-8"))
    }
    val wsRequest = wsClient.url(params.url + path)
      .withQueryString(params.queryParameters.toList: _*)
      .withHeaders(params.headers.toList: _*)

    logger.warn(s"Making request $wsRequest")

    val response = eventualAuthInfo flatMap { authInfo =>
      val signedRequest = wsRequest.sign(oauth1service.sign(authInfo))
      params.method match {
        case ApiEndpointMethod.Get(_)        => signedRequest.get()
        case ApiEndpointMethod.Post(_, body) => signedRequest.post(body)
        case ApiEndpointMethod.Delete(_)     => signedRequest.delete()
        case ApiEndpointMethod.Put(_, body)  => signedRequest.put(body)
      }
    }

    response
  }
}

