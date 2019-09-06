/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 7, 2019
 */

package com.hubofallthings.dataplugUber.apiInterfaces

import com.google.inject.Inject
import com.hubofallthings.dataplug.apiInterfaces.DataPlugOptionsCollector
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointCall, ApiEndpointMethod, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.hubofallthings.dataplugUber.apiInterfaces.authProviders.UberProvider
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

class UberList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: UberProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "uber"
  val endpoint: String = "rides"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.uber.com",
    "/v1.2/history",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def generateEndpointChoices(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    staticEndpointChoices ++ generatePlacesEndpoints
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val profileVariant = ApiEndpointVariant(
      ApiEndpoint("profile", "User's uber profile", None),
      Some(""), Some(""),
      Some(UberProfileInterface.defaultApiEndpoint))

    val historyVariant = ApiEndpointVariant(
      ApiEndpoint("rides", "User's uber history", None),
      Some(""), Some(""),
      Some(UberRidesHistoryInterface.defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("profile", "User's uber profile", active = true, profileVariant),
      ApiEndpointVariantChoice("rides", "User's uber history", active = true, historyVariant))
  }

  def generatePlacesEndpoints: Seq[ApiEndpointVariantChoice] = {
    val workPathParameters = UberSavedPlacesInterface.defaultApiEndpoint.pathParameters + ("placeId" -> "work")
    val workVariant = ApiEndpointVariant(
      ApiEndpoint("places", "User's saved places on Uber", None),
      Some(""), Some(""),
      Some(UberSavedPlacesInterface.defaultApiEndpoint.copy(
        pathParameters = workPathParameters)))
    val workEndpointVariant = ApiEndpointVariantChoice("places", "User's work place on Uber", active = true, workVariant)

    val homePathParameters = UberSavedPlacesInterface.defaultApiEndpoint.pathParameters + ("placeId" -> "home")
    val homeVariant = ApiEndpointVariant(
      ApiEndpoint("places", "User's saved places on Uber", None),
      Some(""), Some(""),
      Some(UberSavedPlacesInterface.defaultApiEndpoint.copy(
        pathParameters = homePathParameters)))
    val homeEndpointVariant = ApiEndpointVariantChoice("places", "User's home place on Uber", active = true, homeVariant)

    Seq(workEndpointVariant, homeEndpointVariant)
  }
}
