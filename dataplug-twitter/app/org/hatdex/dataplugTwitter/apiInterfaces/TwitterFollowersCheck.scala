/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugTwitter.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider
import org.hatdex.dataplug.apiInterfaces.{ DataPlugEndpointInterface, DataPlugOptionsCollector }
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticatorOAuth1
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugTwitter.apiInterfaces.TwitterFriendInterface
import org.hatdex.hat.api.models.ApiDataTable
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.json.JsValue
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class TwitterFollowersCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val provider: TwitterProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth1 {

  val namespace: String = "twitter"
  val endpoint: String = "followers"
  protected val logger: Logger = Logger("TwitterFollowersInterface")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.twitter.com",
    "/1.1/followers/list.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "1"),
    Map())

  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    val authenticatedFetchParameters = authenticateRequest(fetchParams, hatAddress)

    authenticatedFetchParameters flatMap { requestParams =>
      buildRequest(requestParams) flatMap { result =>
        result status match {
          case OK =>
            val variant = ApiEndpointVariant(
              ApiEndpoint("Followers", "Followers of the current user", None),
              Some(""), Some(""),
              Some(TwitterFriendInterface.defaultApiEndpoint)
            )

            val choices = Seq(ApiEndpointVariantChoice("followers", "Followers", active = true, variant))
            Future.successful(choices)
          case _ =>
            logger.warn(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}")
            Future.failed(new RuntimeException(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}"))
        }
      }
    }
  }

  override protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] =
    super[RequestAuthenticatorOAuth1].buildRequest(params)

}
