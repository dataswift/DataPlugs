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
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import com.mohiva.play.silhouette.impl.providers.oauth2.InstagramProvider
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
    "https://api.instagram.com/v1",
    "/users/self",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = staticEndpointChoices

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val profileVariant = ApiEndpointVariant(
      ApiEndpoint("profile", "User's Instagram profile information", None),
      Some(""), Some(""),
      Some(InstagramProfileInterface.defaultApiEndpoint))

    val feedVariant = ApiEndpointVariant(
      ApiEndpoint("feed", "User's Instagram posts feed", None),
      Some(""), Some(""),
      Some(InstagramFeedInterface.defaultApiEndpoint))

    val choices = Seq(
      ApiEndpointVariantChoice("profile", "User's Instagram profile information", active = true, profileVariant),
      ApiEndpointVariantChoice("feed", "User's Instagram posts feed", active = true, feedVariant))

    choices
  }

  override def attachAccessToken(params: ApiEndpointCall, authInfo: OAuth2Info): ApiEndpointCall =
    params.copy(queryParameters = params.queryParameters + ("access_token" -> authInfo.accessToken))
}
