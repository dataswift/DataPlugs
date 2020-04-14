/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 2 2020
 */

package com.hubofallthings.dataplugInstagram.apiInterfaces.authProviders

import java.net.URLEncoder.encode

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, HTTPLayer }
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.{ SocialStateHandler, _ }
import InstagramProvider._
import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider.AuthorizationURLUndefined
import play.api.libs.json._
import play.api.mvc.{ Result, Results }

import scala.concurrent.Future

/**
 * Base Instagram OAuth2 Provider.
 *
 * @see https://docs.yapily.com/?version=latest
 */
trait BaseInstagramProvider extends OAuth2Provider {

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
      logger.debug(s"Access token url is: $url")
      url.get().flatMap { response =>
        logger.debug(s"Response is: $response")
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
        (json \ "access_token").asOpt[String] match {
          case Some(longLivedAccessToken) => Future.successful(authInfo.copy(accessToken = longLivedAccessToken))
          case None                       => throw new ProfileRetrievalException(SpecifiedProfileError.format(id, 401, "Cannot refresh instagram token", ""))
        }
      }
  }

  override def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, OAuth2Info]] = {
    logger.debug(s"instagram state is ${request.extractString(State).getOrElse("")}")
    handleFlow(handleAuthorizationFlow(stateHandler)) { code =>
      logger.debug(s"instagram state before unserializing ${request.extractString(State).getOrElse("").replace("__", "%3D%3D")}")
      //      stateHandler.unserializingerialize(request.extractString(State).getOrElse("").replace("__", "%3D%3D")).flatMap { _ =>
      getAccessToken(code).map(oauth2Info => oauth2Info)
    }
  }

  override protected def handleAuthorizationFlow[B](stateHandler: SocialStateHandler)(implicit request: ExtractableRequest[B]): Future[Result] = {
    stateHandler.state.map { state =>
      logger.debug(s"[Silhouette][%s] State parameter before serialisation: ${state.items}")
      val serializedState = stateHandler.serialize(state) //.split("==").headOption.getOrElse("")
      //      val token = serializedState.split("==").drop(1).headOption.getOrElse("")
      logger.debug(s"[Silhouette][%s] State parameter after serialisation: ${serializedState}")
      val stateParam = if (serializedState.isEmpty) List() else List(State -> serializedState)
      val redirectParam = settings.redirectURL match {
        case Some(rUri) => List((RedirectURI, resolveCallbackURL(rUri)))
        case None       => Nil
      }
      val params = settings.scope.foldLeft(List(
        (ClientID, settings.clientID),
        (ResponseType, Code)) ++ stateParam ++ settings.authorizationParams.toList ++ redirectParam) {
        case (p, s) => (Scope, s) :: p
      }
      val encodedParams = params.map { p => encode(p._1, "UTF-8") + "=" + encode(p._2, "UTF-8") }
      val url = settings.authorizationURL.getOrElse {
        throw new ConfigurationException(AuthorizationURLUndefined.format(id))
      } + encodedParams.mkString("?", "&", "")
      val nUrl = url.replace("%3D%3D", "__")
      logger.debug(s"Url is: $nUrl")
      val redirect = stateHandler.publish(Results.Redirect(nUrl), state)
      logger.debug("[Silhouette][%s] Use authorization URL: %s".format(id, settings.authorizationURL))
      logger.debug("[Silhouette][%s] Redirecting to: %s".format(id, nUrl))
      redirect
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

