/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 7, 2019
 */

package com.hubofallthings.dataplugUber.apiInterfaces.authProviders

import com.hubofallthings.dataplug.apiInterfaces.authProviders.DataPlugDisconnect
import com.hubofallthings.dataplugUber.apiInterfaces.authProviders.UberProvider._
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import play.api.http.HeaderNames._
import play.api.libs.json.{ JsObject, JsValue }

import scala.concurrent.Future

/**
 * Base Uber OAuth2 Provider.
 *
 * @see https://monzo.com/docs/#authentication
 */
trait BaseUberProvider extends DataPlugDisconnect with OAuth2Provider {

  /**
   * The content type to parse a profile from.
   */
  override type Content = JsValue

  /**
   * The provider ID.
   */
  override val id = ID

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   */
  override protected val urls = Map("api" -> settings.apiURL.getOrElse(API))

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api")).withHttpHeaders(AUTHORIZATION -> s"Bearer ${authInfo.accessToken}").get().flatMap { response =>
      val json = response.json
      (json \ "error").asOpt[JsObject] match {
        case Some(error) =>
          val errorCode = (error \ "code").as[Int]
          val errorMsg = (error \ "message").as[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorCode, errorMsg))
        case _ => profileParser.parse(json, authInfo)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class UberProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = (json \ "uuid").as[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID))
  }
}

/**
 * The Uber OAuth2 Provider.
 *
 * @param httpLayer     The HTTP layer implementation.
 * @param stateHandler The state provider implementation.
 * @param settings      The provider settings.
 */
class UberProvider(
    protected val httpLayer: HTTPLayer,
    protected val stateHandler: SocialStateHandler,
    val settings: OAuth2Settings)
  extends BaseUberProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  type Self = UberProvider

  /**
   * The profile parser implementation.
   */
  val profileParser = new UberProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  def withSettings(f: (Settings) => Settings) = new UberProvider(httpLayer, stateHandler, f(settings))
}

/**
 * The companion object.
 */
object UberProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Uber constants.
   */
  val ID = "uber"
  val API = "https://api.uber.com/v1.2/me"
}

