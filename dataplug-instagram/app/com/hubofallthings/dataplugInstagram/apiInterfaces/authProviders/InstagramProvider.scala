/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 2 2020
 */

package com.hubofallthings.dataplugInstagram.apiInterfaces.authProviders

import com.hubofallthings.dataplug.apiInterfaces.authProviders.DataPlugDisconnect
import com.hubofallthings.dataplugInstagram.apiInterfaces.authProviders.InstagramProvider._
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, HTTPLayer }
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import play.api.libs.json._
import play.api.mvc.Result

import scala.concurrent.Future

/**
 * Base Instagram OAuth2 Provider.
 *
 * @see https://docs.yapily.com/?version=latest
 */
trait BaseInstagramProvider extends DataPlugDisconnect with OAuth2Provider {

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
    getLongLivedToken(authInfo).flatMap { refreshedTokenAuthInfo =>
      val url = httpLayer.url(urls("api").format(refreshedTokenAuthInfo.accessToken)).addQueryStringParameters(("access_token", refreshedTokenAuthInfo.accessToken))
      url.get().flatMap { response =>
        val json = response.json
        (json \ "code").asOpt[Int] match {
          case Some(code) if code != 200 =>
            val errorType = (json \ "error_type").asOpt[String]
            val errorMsg = (json \ "error_message").asOpt[String]

            throw new ProfileRetrievalException(SpecifiedProfileError.format(id, code, errorType, errorMsg))
          case _ => profileParser.parse(json, refreshedTokenAuthInfo)
        }
      }
    }
  }

  private def getLongLivedToken(authInfo: OAuth2Info): Future[OAuth2Info] = {
    val clientSecret = settings.clientSecret
    httpLayer
      .url(s"https://graph.instagram.com/access_token?grant_type=ig_exchange_token&client_secret=$clientSecret&access_token=${authInfo.accessToken}")
      .get().flatMap { response =>

        val json = response.json
        json.asOpt[OAuth2Info] match {
          case Some(instagramOAuthInfo) => Future.successful(instagramOAuthInfo)
          case None                     => throw new ProfileRetrievalException(SpecifiedProfileError.format(id, 401, "Cannot refresh instagram token", ""))
        }
      }
  }

  /**
   * Starts the authentication process.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return Either a Result or the auth info from the provider.
   */
  override def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, OAuth2Info]] = {
    handleFlow(handleAuthorizationFlow(stateHandler)) { code =>
      /*
      Instagram was failing authorization, both for v1 and v2 APIs, with a Bad Signature error. After a thorough research
      we identified that the problem lies in the `state` parameter and how Silhouette and Instagram form and parse it.
      Specifically the `state` parameter Silhouette is sending, not only to instagram but on all social providers, has the
      following format: `1-signature-data==-HAT token== example:

      [DEBUG] [2020-04-07 22:44:46] c.h.d.u.ImprovedJcaSigner - State parameter is
      1-1dd74ac1125e19d8de39fa4afb7ad44ad8a9798c-Y3NyZi1zdGF0ZQ%3D%3D-eyJ0b2tlbiI6IjE5ZjNjMjQ3NDE0NWQyZGQxMmIyODljNjU5ZDNhYWU2ZDk2OWE1N2UzNDAxMzM1YmY3ZjEwNWE2YzU3YzJiOGEwOGIzNzE5MTlhNTEzZTZiNmUxZTYxZjExMGI1MDJiNjJiZDMwNjQ1NzBhZjM5ZDYyNzBhMWUwMmE3NzQxOGVhMTA0NDQ0NGY4YWQxYjc0ZDRiMjdlM2RiNDRiN2JkY2IwMTI4NjFkMWYwMzIwNWI5OGNjNjdhZTdmMmZlMTVhYzJmNjU0YzE1Y2U4NTVhYTMwMDY3MWQ5MDdlMmE2NzhmNzFkNmVlZTU0NTIyMjZkNWY2NzAxMTUwY2M5NmEwNWIifQ%3D%3D

      Instagram by default removes the token, because of the = character. Upon successful authorization the `state` parameter
      returned by Instagram has been stripped from the token. This results in the signatures between what the Data Plug
      sent and what Instagram returns to be different and as a result Silhouette throws the Bad Signature error. To test
      that it's indeed the = character that is causing the issues we replaced the == with __ just before the redirect to
      instagram happens. As expected, Instagram is not removing the token in this case. In this case, the `state` parameter
      has been modified and us a result we weren't able to pass the validation step once again. Removing the token entirely
      has another effect down the line, Silhouette is not able to identify which user connected the Data Plug and it throws
      another exception this time.

      As a result we concluded to disable the CSRF check for the time being. Also filled a bug report to Facebook that's
      currently under investigation.

      Issue reported to facebook:
      https://developers.facebook.com/support/bugs/511886289481673/
      command that has been removed here:
      stateHandler.unserialize(request.extractString(State).getOrElse("")).flatMap { _ => ... }
      */
      getAccessToken(code).map(oauth2Info => oauth2Info)
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class InstagramProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = (json \ "id").as[String]
    CommonSocialProfile(loginInfo = LoginInfo(ID, userID))
  }
}

/**
 * The Instagram OAuth2 Provider.
 *
 * @param httpLayer     The HTTP layer implementation.
 * @param stateHandler The state provider implementation.
 * @param settings      The provider settings.
 */
class InstagramProvider(
    protected val httpLayer: HTTPLayer,
    protected val stateHandler: SocialStateHandler,
    val settings: OAuth2Settings)
  extends BaseInstagramProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  type Self = InstagramProvider

  /**
   * The profile parser implementation.
   */
  val profileParser = new InstagramProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  def withSettings(f: (Settings) => Settings) = new InstagramProvider(httpLayer, stateHandler, f(settings))
}

/**
 * The companion object.
 */
object InstagramProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Instagram constants.
   */
  val ID = "instagram"
  val API = "https://graph.instagram.com/me"
}
