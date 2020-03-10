/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.apiInterfaces

import java.net.URLEncoder

import com.google.inject.Inject
import com.hubofallthings.dataplug.actors.Errors.SourceApiCommunicationException
import com.hubofallthings.dataplug.apiInterfaces.DataPlugOptionsCollector
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointCall, ApiEndpointMethod, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.hubofallthings.dataplugYapily.apiInterfaces.authProviders.YapilyProvider
import com.hubofallthings.dataplugYapily.models.YapilyAccounts
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.{ WSAuthScheme, WSClient, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }

class YapilyList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: YapilyProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "yapily"
  val endpoint: String = "accounts"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.yapily.com",
    "/accounts",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  override protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val token: String = params.headers.getOrElse("Authorization", "").split(' ').last
    val path = params.pathParameters.foldLeft(params.path) { (path, parameter) =>
      path.replace(s"[${parameter._1}]", URLEncoder.encode(parameter._2, "UTF-8"))
    }
    val wsRequest = wsClient.url(params.url + path)
      .withAuth(provider.settings.clientID, provider.settings.clientSecret, WSAuthScheme.BASIC)
      .withQueryStringParameters(params.queryParameters.toList: _*)
      .addHttpHeaders("Consent" -> token)

    val response = params.method match {
      case ApiEndpointMethod.Get(_)        => wsRequest.get()
      case ApiEndpointMethod.Post(_, body) => wsRequest.post(body)
      case ApiEndpointMethod.Delete(_)     => wsRequest.delete()
      case ApiEndpointMethod.Put(_, body)  => wsRequest.put(body)
    }

    response recover {
      case e => throw SourceApiCommunicationException(s"Error executing request $params", e)
    }
  }

  def generateEndpointChoices(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    staticEndpointChoices ++ generateTransactionsEndpoints(maybeResponseBody)
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val accountsVariant = ApiEndpointVariant(
      ApiEndpoint("accounts", "User's bank accounts", None),
      Some(""), Some(""),
      Some(YapilyAccountsInterface.defaultApiEndpoint))
    val identityVariant = ApiEndpointVariant(
      ApiEndpoint("identity", "User's bank identity", None),
      Some(""), Some(""),
      Some(YapilyIdentityInterface.defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("accounts", "User's bank accounts", active = true, accountsVariant),
      ApiEndpointVariantChoice("identity", "User's bank identity", active = true, identityVariant))
  }

  private def generateTransactionsEndpoints(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    maybeResponseBody.flatMap { responseBody =>
      (responseBody \ "data").asOpt[Seq[YapilyAccounts]].map { accounts =>
        accounts.map { account =>
          val pathParameters = YapilyTransactionsInterface.defaultApiEndpoint.pathParameters + ("accountId" -> account.id)
          val variant = ApiEndpointVariant(
            ApiEndpoint("transactions", "User's bank transactions", None),
            Some(account.id), Some(account.description.getOrElse(s"${account.`type`.getOrElse("")}")),
            Some(YapilyTransactionsInterface.defaultApiEndpoint.copy(pathParameters = pathParameters)))

          ApiEndpointVariantChoice(account.id, account.description.getOrElse(s"${account.`type`.getOrElse("")}"), active = true, variant)
        }
      }
    }.getOrElse(Seq())
  }

}
