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
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticatorOAuth1
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }

class TwitterTweetsCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val mailer: Mailer,
    val provider: TwitterProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth1 {

  val namespace: String = "twitter"
  val endpoint: String = "tweets"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.twitter.com",
    "/1.1/statuses/user_timeline.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "1"),
    Map(),
    Some(Map()))

  def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = staticEndpointChoices

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val variantTweets = ApiEndpointVariant(
      ApiEndpoint("tweets", "Tweets", None),
      Some(""), Some(""),
      Some(TwitterTweetInterface.defaultApiEndpoint))

    val variantFollowers = ApiEndpointVariant(
      ApiEndpoint("followers", "Followers", None),
      Some(""), Some(""),
      Some(TwitterFollowerInterface.defaultApiEndpoint))

    val variantFriends = ApiEndpointVariant(
      ApiEndpoint("friends", "Friends", None),
      Some(""), Some(""),
      Some(TwitterFriendInterface.defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("tweets", "My Tweets", active = true, variantTweets),
      ApiEndpointVariantChoice("followers", "My Followers", active = true, variantFollowers),
      ApiEndpointVariantChoice("friends", "My Friends", active = true, variantFriends))
  }

  override protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] =
    super[RequestAuthenticatorOAuth1].buildRequest(params)

}

