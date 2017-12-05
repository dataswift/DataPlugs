package org.hatdex.dataplugSpotify.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugSpotify.apiInterfaces.authProviders.SpotifyProvider
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class SpotifyProfileCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: SpotifyProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "spotify"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.spotify.com",
    "/v1/me",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    authenticateRequest(fetchParams, hatAddress, refreshToken = false).flatMap { requestParams =>
      logger.info("Spotify profile check authenticated")
      buildRequest(requestParams).flatMap { response =>
        response.status match {
          case OK =>
            val profileVariant = ApiEndpointVariant(
              ApiEndpoint("profile", "User's Spotify profile information", None),
              Some(""), Some(""),
              Some(SpotifyProfileInterface.defaultApiEndpoint))

            //            val activityVariant = ApiEndpointVariant(
            //              ApiEndpoint("temp", "User's Spotify activity list", None),
            //              Some(""), Some(""),
            //              Some(SpotifyTempInterface.defaultApiEndpoint))

            val choices = Seq(
              ApiEndpointVariantChoice("profile", "User's Spotify profile information", active = true, profileVariant))
            //              ApiEndpointVariantChoice("activity", "User's Spotify activity list", active = true, activityVariant))

            logger.info(s"API endpoint SpotifyProfile validated for $hatAddress")
            Future.successful(choices)

          case _ =>
            logger.warn(s"Could not validate SpotifyProfile API endpoint $fetchParams - ${response.status}: ${response.body}")
            Future.failed(SourceDataProcessingException("Could not validate FitbitProfile API endpoint"))
        }
      }
    }.recover {
      case e =>
        logger.error(s"Failed to validate SpotifyProfile API endpoint. Reason: ${e.getMessage}", e)
        throw e
    }
  }

}
