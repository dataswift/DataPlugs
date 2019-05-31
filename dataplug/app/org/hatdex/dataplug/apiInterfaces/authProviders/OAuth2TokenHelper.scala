/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.apiInterfaces.authProviders

import javax.inject.Inject

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.{ OAuth2Info, OAuth2Provider, SocialProviderRegistry }
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.hatdex.dataplug.actors.Errors.SourceAuthenticationException
import play.api.cache.{ AsyncCacheApi }
import play.api.libs.ws.{ WSClient, WSResponse }
import play.api.{ Configuration, Logger }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class OAuth2TokenHelper @Inject() (
    configuration: Configuration,
    wsClient: WSClient,
    authInfoRepository: AuthInfoRepository,
    cache: AsyncCacheApi,
    socialProviderRegistry: SocialProviderRegistry) {
  protected val logger = Logger(this.getClass)

  /**
   * Refreshes the OAuth2Info token at the refreshURL.
   *
   * Caches previously refreshed tokens to avoid unnecessarily hitting the provider's endpoints with every API call
   *
   * @param refreshToken The refresh token, as on OAuth2Info
   */
  def refresh(loginInfo: LoginInfo, refreshToken: String)(implicit ec: ExecutionContext): Option[Future[OAuth2Info]] = {
    socialProviderRegistry.get[OAuth2Provider](loginInfo.providerID) match {
      case Some(p: OAuth2Provider) =>
        implicit val provider: OAuth2Provider = p
        val settings = configuration.underlying.as[OAuth2SettingsExtended](s"silhouette.${loginInfo.providerID}")
        settings.refreshURL.map({ url =>
          val encodedAuth = Base64.encode(s"${settings.clientID}:${settings.clientSecret}")
          val params = Map(
            "client_id" -> Seq(p.settings.clientID),
            "client_secret" -> Seq(p.settings.clientSecret),
            "grant_type" -> Seq("refresh_token"),
            "refresh_token" -> Seq(refreshToken)) ++ p.settings.scope.map({
              "scope" -> Seq(_)
            })

          val refreshQueryParams = if (settings.customProperties.getOrElse("parameters_location", "") == "query") {
            Map("grant_type" -> "refresh_token", "refresh_token" -> refreshToken)
          }
          else {
            Map()
          }

          val authHeader = p.settings.customProperties
            .get("authorization_header_prefix")
            .map(_ + " ")
            .getOrElse("")
            .concat(encodedAuth)

          wsClient.url(url)
            .withHttpHeaders(settings.refreshHeaders.toSeq: _*)
            .addHttpHeaders("Authorization" -> authHeader)
            .withQueryStringParameters(refreshQueryParams.toSeq: _*)
            .post(params)
            .flatMap(resp => Future.fromTry(buildInfo(resp)))
        })
      case _ =>
        logger.info(s"No OAuth2Provider for $loginInfo, $refreshToken")
        None
    }

  }

  /**
   * Builds the OAuth2 info from response.
   *
   * @param response The response from the provider.
   * @return The OAuth2 info on success, otherwise a failure.
   */
  protected def buildInfo(response: WSResponse)(implicit provider: OAuth2Provider): Try[OAuth2Info] = {
    logger.debug(s"Validate OAuth2Info: ${response.json}")
    response.json.validate[OAuth2Info].asEither match {
      case Left(error) =>
        logger.error(s"Response body is: ${response.json}")
        Failure(SourceAuthenticationException(s"Cannot build OAuth2Info for ${provider.id} token refresh because of invalid response format: $error"))
      case Right(info) => Success(info)}
  }

  /**
   * The extended OAuth2 settings.
   *
   * @param authorizationURL    The authorization URL provided by the OAuth provider.
   * @param accessTokenURL      The access token URL provided by the OAuth provider.
   * @param redirectURL         The redirect URL to the application after a successful authentication on the OAuth
   *                            provider. The URL can be a relative path which will be resolved against the current
   *                            request's host.
   * @param apiURL              The URL to fetch the profile from the API. Can be used to override the default URL
   *                            hardcoded in every provider implementation.
   * @param refreshURL          The token refresh URL to the OAuth provider to refresh token if refresh token is available
   * @param clientID            The client ID provided by the OAuth provider.
   * @param clientSecret        The client secret provided by the OAuth provider.
   * @param scope               The OAuth2 scope parameter provided by the OAuth provider.
   * @param authorizationParams Additional params to add to the authorization request.
   * @param accessTokenParams   Additional params to add to the access token request.
   * @param customProperties    A map of custom properties for the different providers.
   */
  case class OAuth2SettingsExtended(
      authorizationURL: Option[String] = None,
      accessTokenURL: String,
      redirectURL: String,
      apiURL: Option[String] = None,
      refreshURL: Option[String] = None,
      refreshHeaders: Map[String, String] = Map(
        "Accept" -> "application/json",
        "Content-Type" -> "application/x-www-form-urlencoded"),
      clientID: String,
      clientSecret: String,
      scope: Option[String] = None,
      authorizationParams: Map[String, String] = Map.empty,
      accessTokenParams: Map[String, String] = Map.empty,
      customProperties: Map[String, String] = Map.empty)

}
