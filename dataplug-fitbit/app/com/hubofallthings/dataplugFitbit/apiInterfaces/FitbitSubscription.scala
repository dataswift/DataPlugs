/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 8, 2017
 */

package com.hubofallthings.dataplugFitbit.apiInterfaces

import java.util.UUID

import akka.Done
import akka.actor.Scheduler
import com.google.inject.Inject
import com.hubofallthings.dataplug.actors.Errors.SourceDataProcessingException
import com.hubofallthings.dataplug.apiInterfaces.DataPlugApiEndpointClient
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.hubofallthings.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import play.api.Logger
import play.api.http.Status._
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class FitbitSubscription @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FitbitProvider) extends DataPlugApiEndpointClient with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "subscription"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com/1",
    "/user/-/[collection-path]/apiSubscriptions/[subscription].json",
    ApiEndpointMethod.Post("Post", ""),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def create(collectionPath: String, hatAddress: String)(implicit ec: ExecutionContext): Future[Done] = {
    val params = defaultApiEndpoint.copy(pathParameters =
      Map("collection-path" -> collectionPath, "subscription" -> UUID.randomUUID().toString))

    authenticateRequest(params, hatAddress, refreshToken = false).flatMap { requestParams =>
      buildRequest(requestParams).map { response =>
        response.status match {
          case CREATED =>
            logger.info(s"Fitbit API subscription added: ${response.body}")
            Done

          case _ =>
            logger.warn(s"Could not add Fitbit subscription with $params - ${response.status}: ${response.body}")
            throw SourceDataProcessingException(s"Could not add Fitbit subscription with $params - ${response.status}")
        }
      }
    }
  }

  def delete(collectionPath: String, hatAddress: String)(implicit ec: ExecutionContext): Future[Done] = {
    // FIXME: need to delete a specific subscription ID!
    val params = defaultApiEndpoint.copy(
      pathParameters =
        Map("collection-path" -> collectionPath, "subscription" -> UUID.randomUUID().toString),
      method = ApiEndpointMethod.Delete("Delete"))

    authenticateRequest(params, hatAddress, refreshToken = false).flatMap { requestParams =>
      buildRequest(requestParams).map { response =>
        response.status match {
          case OK =>
            logger.info(s"Fitbit API subscription deleted: ${response.body}")
            Done

          case _ =>
            logger.warn(s"Could not delete Fitbit subscription with $params - ${response.status}: ${response.body}")
            throw SourceDataProcessingException(s"Could not delete Fitbit subscription with $params - ${response.status}")
        }
      }
    }
  }
}
