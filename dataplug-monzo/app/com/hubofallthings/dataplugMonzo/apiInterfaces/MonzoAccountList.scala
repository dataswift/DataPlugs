/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 11, 2017
 */

package com.hubofallthings.dataplugMonzo.apiInterfaces

import com.google.inject.Inject
import com.hubofallthings.dataplug.apiInterfaces.DataPlugOptionsCollector
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointCall, ApiEndpointMethod, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.hubofallthings.dataplugMonzo.apiInterfaces.authProviders.MonzoProvider
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class MonzoAccountList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: MonzoProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "monzo"
  val endpoint: String = "accounts"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.monzo.com",
    "/accounts",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def generateEndpointChoices(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    staticEndpointChoices ++ generateAccountsEndpointChoices(maybeResponseBody)
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val accountsVariant = ApiEndpointVariant(
      ApiEndpoint("accounts", "User's monzo accounts", None),
      Some(""), Some(""),
      Some(defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("accounts", "User's monzo accounts", active = true, accountsVariant))
  }

  def generateAccountsEndpointChoices(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    maybeResponseBody.map { responseBody =>
      (responseBody \ "accounts").as[Seq[JsValue]] map { account =>
        val accountId = (account \ "id").as[String]
        val description = (account \ "description").as[String]
        val queryParameters = MonzoTransactionsInterface.defaultApiEndpoint.queryParameters + ("account_id" -> accountId)
        val variant = ApiEndpointVariant(
          ApiEndpoint("transactions", "Monzo Transactions", None),
          Some(accountId), Some(description),
          Some(MonzoTransactionsInterface.defaultApiEndpoint.copy(queryParameters = queryParameters)))

        ApiEndpointVariantChoice(accountId, description, active = false, variant)
      }
    }.getOrElse(Seq())
  }
}
