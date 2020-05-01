/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 5, 2020
 */

package com.hubofallthings.dataplugFacebook.apiInterfaces.authProviders

import com.hubofallthings.dataplug.apiInterfaces.authProviders.HatOAuth2Provider
import com.hubofallthings.dataplugFacebook.models.FacebookProfile
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider._
import play.api.libs.json.{ JsObject, JsValue }

import scala.concurrent.Future

/**
 * Base Facebook OAuth2 Provider.
 *
 * @see https://developers.facebook.com/tools/explorer
 * @see https://developers.facebook.com/docs/graph-api/reference/user
 * @see https://developers.facebook.com/docs/facebook-login/access-tokens
 */
trait BaseFacebookProvider extends HatOAuth2Provider {

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
    httpLayer.url(urls("api").format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "error").asOpt[JsObject] match {
        case Some(error) =>
          val errorMsg = (error \ "message").as[String]
          val errorType = (error \ "type").as[String]
          val errorCode = (error \ "code").as[Int]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorMsg, errorType, errorCode))
        case _ => profileParser.parse(json, authInfo)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class FacebookProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = {
    val maybeProfile = json.asOpt[FacebookProfile]
    maybeProfile match {
      case Some(profile) =>
        val socialProfile = CommonSocialProfile(
          loginInfo = LoginInfo(ID, profile.id),
          firstName = Option(profile.first_name),
          lastName = Option(profile.last_name),
          fullName = profile.full_name,
          avatarURL = profile.profile_pic,
          email = profile.email)
        Future.successful(socialProfile)

      case None => Future.failed(new ProfileRetrievalException("Could not parse profile information"))
    }
  }
}

/**
 * The Facebook OAuth2 Provider.
 *
 * @param httpLayer     The HTTP layer implementation.
 * @param stateHandler  The state provider implementation.
 * @param settings      The provider settings.
 */
class FacebookProvider(
    protected val httpLayer: HTTPLayer,
    protected val stateHandler: SocialStateHandler,
    val settings: OAuth2Settings)
  extends BaseFacebookProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = FacebookProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new FacebookProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = new FacebookProvider(httpLayer, stateHandler, f(settings))
}

/**
 * The companion object.
 */
object FacebookProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Uber constants.
   */
  val ID = "facebook"
  val API = "https://graph.facebook.com/v5.0/me?fields=name,first_name,last_name,picture,email&return_ssl_resources=1&access_token=%s"
}

