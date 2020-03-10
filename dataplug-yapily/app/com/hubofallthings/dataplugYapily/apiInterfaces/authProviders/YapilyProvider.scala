/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.apiInterfaces.authProviders

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, HTTPLayer }
import com.mohiva.play.silhouette.impl.exceptions.{ AccessDeniedException, UnexpectedResponseException }
import com.mohiva.play.silhouette.impl.providers._
import com.hubofallthings.dataplugYapily.apiInterfaces.authProviders.YapilyProvider._
import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider.{ AuthorizationError, AuthorizationURLUndefined, InvalidInfoFormat }
import play.api.libs.json.{ JsValue, Json }
import play.api.libs.ws.WSAuthScheme
import play.api.mvc.{ Result, Results }

import scala.concurrent.Future

/**
 * Base Yapily OAuth2 Provider.
 *
 * @see https://monzo.com/docs/#authentication
 */
trait BaseYapilyProvider extends OAuth2Provider {

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
    val test = CommonSocialProfile(LoginInfo(providerID = "yapily", providerKey = "test")).asInstanceOf[Profile]
    Future.successful(test)
  }

  override def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, OAuth2Info]] = {
    val redirect: String = settings.redirectURL.getOrElse("")
    val body = Json.obj(
      "userUuid" -> "00e7e138-01c9-4515-b830-cdbe71f24714",
      "institutionId" -> "barclays-sandbox",
      "callback" -> redirect)
    logger.debug(s"Body is $body")

    httpLayer.url(settings.authorizationURL.getOrElse(""))
      .addHttpHeaders(("Content-Type", "application/json"))
      .withAuth(settings.clientID, settings.clientSecret, WSAuthScheme.BASIC)
      .post(body).map { response =>
        response.status match {
          case 201 =>
            val auth = (response.json \ "data" \ "authorisationUrl").asOpt[String]
            handleFlow(handleCustomAuthorizationFlow(stateHandler, auth)) { code =>
              val updatedJson = Json.obj("access_token" -> code)
              updatedJson.validate[OAuth2Info].asEither.fold(
                error => Future.failed(new UnexpectedResponseException(InvalidInfoFormat.format(id, error))),
                info => Future.successful(info))
            }
          case _ =>
            logger.debug(s"AuthURL not found")
            handleFlow(handleCustomAuthorizationFlow(stateHandler, None)) { code =>
              val updatedJson = Json.obj("access_token" -> code)
              updatedJson.validate[OAuth2Info].asEither.fold(
                error => Future.failed(new UnexpectedResponseException(InvalidInfoFormat.format(id, error))),
                info => Future.successful(info))
            }
        }
      }.flatten
  }

  protected def handleCustomAuthorizationFlow[B](stateHandler: SocialStateHandler, authUrl: Option[String])(implicit request: ExtractableRequest[B]): Future[Result] = {
    stateHandler.state.map { state =>
      val serializedState = stateHandler.serialize(state)
      val url = authUrl.getOrElse {
        throw new ConfigurationException(AuthorizationURLUndefined.format(id))
      } + s"&$State=$serializedState"
      logger.debug(s"Attached state parameter to the request $url")
      val redirect = stateHandler.publish(Results.Redirect(url), state)
      logger.debug("[Silhouette][%s] Use authorization URL: %s".format(id, authUrl))
      logger.debug("[Silhouette][%s] Redirecting to: %s".format(id, url))
      redirect
    }
  }

  override def handleFlow[L, R, B](left: => Future[L])(right: String => Future[R])(implicit request: ExtractableRequest[B]): Future[Either[L, R]] = {
    request.extractString(Error).map {
      case e @ AccessDenied => new AccessDeniedException(AuthorizationError.format(id, e))
      case e                => new UnexpectedResponseException(AuthorizationError.format(id, e))
    } match {
      case Some(throwable) => Future.failed(throwable)
      case None =>
        logger.debug(s"Request is $request")

        request.extractString("consent") match {
          // We're being redirected back from the authorization server with the access code and the state
          case Some(code) => right(code).map(Right.apply)
          // There's no code in the request, this is the first step in the OAuth flow
          case None       => left.map(Left.apply)
        }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class YapilyProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = (json \ "uuid").as[String]
    CommonSocialProfile(loginInfo = LoginInfo(ID, userID))
  }
}

/**
 * The Uber OAuth2 Provider.
 *
 * @param httpLayer     The HTTP layer implementation.
 * @param stateHandler The state provider implementation.
 * @param settings      The provider settings.
 */
class YapilyProvider(
    protected val httpLayer: HTTPLayer,
    protected val stateHandler: SocialStateHandler,
    val settings: OAuth2Settings)
  extends BaseYapilyProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  type Self = YapilyProvider

  /**
   * The profile parser implementation.
   */
  val profileParser = new YapilyProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  def withSettings(f: (Settings) => Settings) = new YapilyProvider(httpLayer, stateHandler, f(settings))
}

/**
 * The companion object.
 */
object YapilyProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Yapily constants.
   */
  val ID = "yapily"
  val API = "https://api.yapily.com/me" //identity
}
