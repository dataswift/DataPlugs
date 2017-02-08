/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugTwitter.apiInterfaces

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider
import org.hatdex.dataplug.apiInterfaces.DataPlugContentUploader
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticatorOAuth1
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugTwitter.models.{ TwitterStatusUpdate }
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }

class TwitterStatusUpdateInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val provider: TwitterProvider) extends DataPlugContentUploader with RequestAuthenticatorOAuth1 {

  protected val logger: Logger = Logger("TwitterStatusUpdateInterface")
  val sourceName: String = "twitter"
  val endpointName: String = "status_update"

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.twitter.com",
    "/1.1/statuses/[action].json",
    ApiEndpointMethod.Post("Post", ""),
    Map(),
    Map(),
    Map("Content-Type" -> "application/json")
  )

  def post(hatAddress: String, content: String)(implicit ec: ExecutionContext): Future[TwitterStatusUpdate] = {
    logger.info(s"Posting new tweet for $hatAddress")

    val requestParamsWithQueryParams = defaultApiEndpoint.copy(
      queryParameters = Map("status" -> content),
      pathParameters = Map("action" -> "update"))

    val authenticatedFetchParameters = authenticateRequest(requestParamsWithQueryParams, hatAddress)

    authenticatedFetchParameters flatMap { requestParams =>
      buildRequest(requestParams) flatMap { result =>
        result status match {
          case OK =>
            Future.successful(Json.parse(result.body).as[TwitterStatusUpdate])
          case status =>
            Future.failed(new RuntimeException(s"Unexpected response from twitter (status code $status): ${result.body}"))
        }
      }
    }
  }

  def delete(hatAddress: String, providerId: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Deleting tweet $providerId for $hatAddress.")

    val fetchParams = defaultApiEndpoint.copy(
      pathParameters = Map("action" -> "destroy", "id" -> providerId),
      path = "/1.1/statuses/[action]/[id].json")

    val authenticatedFetchParams = authenticateRequest(fetchParams, hatAddress)

    authenticatedFetchParams flatMap { requestParams =>
      buildRequest(requestParams) flatMap { result =>
        result status match {
          case OK =>
            Future.successful()
          case status =>
            Future.failed(new RuntimeException(s"Unexpected response from twitter (status code $status): ${result.body}"))
        }
      }
    }
  }

  override protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] =
    super[RequestAuthenticatorOAuth1].buildRequest(params)
}
