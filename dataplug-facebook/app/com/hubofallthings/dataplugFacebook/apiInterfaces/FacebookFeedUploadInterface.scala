/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 5, 2017
 */

package com.hubofallthings.dataplugFacebook.apiInterfaces

import com.google.inject.Inject
import com.hubofallthings.dataplug.apiInterfaces.DataPlugContentUploader
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider
import com.hubofallthings.dataplug.actors.Errors.{ SourceApiCommunicationException, SourceAuthenticationException }
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod, DataPlugNotableShareRequest }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.hubofallthings.dataplugFacebook.models.FacebookFeedUpdate
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class FacebookFeedUploadInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: FacebookProvider) extends DataPlugContentUploader with RequestAuthenticatorOAuth2 {

  protected val logger: Logger = Logger(this.getClass)
  val defaultApiEndpoint = FacebookFeedUploadInterface.defaultApiEndpoint
  val photoUploadApiEndpoint = FacebookFeedUploadInterface.photoUploadApiEndpoint
  val deleteApiEndpoint = FacebookFeedUploadInterface.deleteApiEndpoint
  val namespace: String = "facebook"
  val endpoint: String = "feed"

  def post(hatAddress: String, content: DataPlugNotableShareRequest)(implicit ec: ExecutionContext): Future[FacebookFeedUpdate] = {
    val apiEndpoint = if (content.photo.isDefined) {
      logger.debug(s"[$hatAddress] Found photo. Uploading to Facebook, caption:\n${content.message}\n photo:\n${content.photo.get}")
      logger.info(s"[$hatAddress] Posting to Facebook with media attached for $hatAddress")
      photoUploadApiEndpoint.copy(
        method = ApiEndpointMethod.Post("Post", Json.stringify(Json.obj(
          "caption" -> content.message,
          "url" -> content.photo.get))))
    }
    else {
      logger.debug(s"[$hatAddress] Found message. Posting to Facebook:\n$content")
      logger.info(s"[$hatAddress] Posting to Facebook w/o media for $hatAddress")
      defaultApiEndpoint.copy(
        method = ApiEndpointMethod.Post("Post", Json.stringify(Json.obj("message" -> content.message))))
    }

    val authenticatedApiEndpoint = authenticateRequest(apiEndpoint, hatAddress)

    authenticatedApiEndpoint flatMap { requestParams =>
      buildRequest(requestParams) flatMap { result =>
        result.status match {
          case OK =>
            Future.successful(Json.parse(result.body).as[FacebookFeedUpdate])
          case BAD_REQUEST =>
            Future.failed(SourceAuthenticationException(
              s"[$hatAddress] Authentication with Facebook failed. Response details: ${result.body}"))
          case status =>
            Future.failed(SourceApiCommunicationException(
              s"[$hatAddress] Unexpected response from facebook (status code $status): ${result.body}"))
        }
      }
    }
  }

  def delete(hatAddress: String, providerId: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Deleting shared facebook post $providerId for $hatAddress.")

    val authenticatedApiCall = authenticateRequest(deleteApiEndpoint.copy(pathParameters = Map("post-id" -> providerId)), hatAddress)

    authenticatedApiCall flatMap { requestParams =>
      buildRequest(requestParams) flatMap { result =>
        result.status match {
          case OK =>
            Future.successful(Unit)
          case status =>
            Future.failed(SourceApiCommunicationException(s"[$hatAddress] Unexpected response from facebook when deleting post $providerId (status code $status): ${result.body}"))
        }
      }
    }
  }
}

object FacebookFeedUploadInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v5.0",
    "/me/feed",
    ApiEndpointMethod.Post("Post", ""),
    Map(),
    Map(),
    Map("Content-Type" -> "application/json"),
    Some(Map()))

  val photoUploadApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v5.0",
    "/me/photos",
    ApiEndpointMethod.Post("Post", ""),
    Map(),
    Map(),
    Map("Content-Type" -> "application/json"),
    Some(Map()))

  val deleteApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v5.0",
    "/[post-id]",
    ApiEndpointMethod.Delete("Delete"),
    Map(),
    Map(),
    Map(),
    Some(Map()))
}
