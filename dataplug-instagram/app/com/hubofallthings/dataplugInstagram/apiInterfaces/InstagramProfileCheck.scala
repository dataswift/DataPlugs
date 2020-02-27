/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugInstagram.apiInterfaces

import akka.actor.Scheduler
import com.google.inject.Inject
import com.hubofallthings.dataplug.apiInterfaces.DataPlugOptionsCollector
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointCall, ApiEndpointMethod, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.hubofallthings.dataplugInstagram.apiInterfaces.authProviders.InstagramProvider
import com.hubofallthings.dataplugInstagram.models.InstagramProfile
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class InstagramProfileCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: InstagramProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "instagram"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.instagram.com",
    "/me",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("fields" -> "username,account_type,media_count"),
    Map(),
    Some(Map()))

  def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    val test = staticEndpointChoices ++ generateMediaEndpoints(responseBody)
    logger.debug(s"endpoints are $test")
    test
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val profileVariant = ApiEndpointVariant(
      ApiEndpoint("profile", "User's Instagram profile information", None),
      Some(""),
      Some(""),
      Some(InstagramProfileInterface.defaultApiEndpoint))

    Seq(ApiEndpointVariantChoice("profile", "User's Instagram profile information", active = true, profileVariant))
  }

  override def attachAccessToken(params: ApiEndpointCall, authInfo: OAuth2Info): ApiEndpointCall =
    params.copy(queryParameters = params.queryParameters + ("access_token" -> authInfo.accessToken))

  private def generateMediaEndpoints(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    maybeResponseBody.map { responseBody =>
      responseBody.asOpt[InstagramProfile].map { profile =>
        val pathParameters = InstagramFeedInterface.defaultApiEndpoint.pathParameters + ("userId" -> profile.id)
        val variant = ApiEndpointVariant(
          ApiEndpoint("feed", "User's Instagram media", None),
          Some(profile.id),
          None,
          Some(InstagramFeedInterface.defaultApiEndpoint.copy(pathParameters = pathParameters)))

        Seq(ApiEndpointVariantChoice(profile.id, "User's Instagram profile information", active = true, variant))
      }.getOrElse(Seq())
    }.getOrElse(Seq())
  }
}
