package org.hatdex.dataplugMonzo.apiInterfaces.authProviders

import java.net.URLEncoder.encode

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, HTTPLayer }
import com.mohiva.play.silhouette.impl.exceptions.{ AccessDeniedException, ProfileRetrievalException, UnexpectedResponseException }
import com.mohiva.play.silhouette.impl.providers._
import MonzoProvider._
import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider.{ AuthorizationError, AuthorizationURLUndefined }
import play.api.libs.json.{ JsObject, JsValue }
import play.api.http.HeaderNames._
import play.api.mvc.{ Result, Results }

import scala.concurrent.Future

/**
 * Base Monzo OAuth2 Provider.
 *
 * @see https://monzo.com/docs/#authentication
 */
trait BaseMonzoProvider extends OAuth2Provider {

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
class MonzoProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = (json \ "user_id").as[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID))
  }
}

/**
 * The Monzo OAuth2 Provider.
 *
 * @param httpLayer     The HTTP layer implementation.
 * @param stateHandler The state provider implementation.
 * @param settings      The provider settings.
 */
class MonzoProvider(
    protected val httpLayer: HTTPLayer,
    protected val stateHandler: SocialStateHandler,
    val settings: OAuth2Settings)
  extends BaseMonzoProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  type Self = MonzoProvider

  /**
   * The profile parser implementation.
   */
  val profileParser = new MonzoProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  def withSettings(f: (Settings) => Settings) = new MonzoProvider(httpLayer, stateProvider, f(settings))

  override def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, OAuth2Info]] = {
    request.extractString(Error).map {
      case e @ AccessDenied => new AccessDeniedException(AuthorizationError.format(id, e))
      case e                => new UnexpectedResponseException(AuthorizationError.format(id, e))
    } match {
      case Some(throwable) => Future.failed(throwable)
      case None => request.extractString(Code) match {
        // We're being redirected back from the authorization server with the access code
        case Some(code) => stateProvider.validate.flatMap { state =>
          logger.debug(s"Authenticate token, validating $state, code $code")
          getAccessToken(code)
            .andThen {
              case r => logger.debug(s"Get access token responded $r")
            }
            .map(oauth2Info => Right(oauth2Info))
        }
        // There's no code in the request, this is the first step in the OAuth flow
        case None => stateProvider.build.map { state =>
          val serializedState = stateProvider.serialize(state)
          val stateParam = if (serializedState.isEmpty) List() else List(State -> serializedState)
          val params = settings.scope.foldLeft(List(
            (ClientID, settings.clientID),
            (RedirectURI, resolveCallbackURL(settings.redirectURL)),
            (ResponseType, Code)) ++ stateParam ++ settings.authorizationParams.toList) {
            case (p, s) => (Scope, s) :: p
          }
          val encodedParams = params.map { p => encode(p._1, "UTF-8") + "=" + encode(p._2, "UTF-8") }
          val url = settings.authorizationURL.getOrElse {
            throw new ConfigurationException(AuthorizationURLUndefined.format(id))
          } + encodedParams.mkString("?", "&", "")
          val redirect = stateProvider.publish(Results.Redirect(url), state)
          logger.debug("[Silhouette][%s] Use authorization URL: %s".format(id, settings.authorizationURL))
          logger.debug("[Silhouette][%s] Redirecting to: %s".format(id, url))
          Left(redirect)
        }
      }
    }
  }
}

/**
 * The companion object.
 */
object MonzoProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Monzo constants.
   */
  val ID = "monzo"
  val API = "https://api.monzo.com/ping/whoami?access_token=%s"
}
