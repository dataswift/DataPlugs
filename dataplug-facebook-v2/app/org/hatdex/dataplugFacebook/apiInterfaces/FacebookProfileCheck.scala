package org.hatdex.dataplugFacebook.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import play.api.Logger
import play.api.http.Status._
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class FacebookProfileCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FacebookProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "facebook"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v2.10",
    "/me",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    authenticateRequest(fetchParams, hatAddress, refreshToken = false).flatMap { requestParams =>
      logger.info("Facebook profile check authenticated")
      buildRequest(requestParams).flatMap { response =>
        response.status match {
          case OK =>
            logger.info(s"API endpoint FacebookProfile validated for $hatAddress")
            Future.successful(staticEndpointChoices)

          case _ =>
            logger.warn(s"Could not validate FacebookProfile API endpoint $fetchParams - ${response.status}: ${response.body}")
            Future.failed(SourceDataProcessingException("Could not validate FacebookProfile API endpoint"))
        }
      }
    }.recover {
      case e =>
        logger.error(s"Failed to validate FacebookProfile API endpoint. Reason: ${e.getMessage}", e)
        throw e
    }
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val profileVariant = ApiEndpointVariant(
      ApiEndpoint("profile", "User's Facebook profile information", None),
      Some(""), Some(""),
      Some(FacebookProfileInterface.defaultApiEndpoint))

    val profilePictureVariant = ApiEndpointVariant(
      ApiEndpoint("profile/picture", "User's Facebook profile picture", None),
      Some(""), Some(""),
      Some(FacebookProfilePictureInterface.defaultApiEndpoint))

    val feedVariant = ApiEndpointVariant(
      ApiEndpoint("feed", "User's Facebook posts feed", None),
      Some(""), Some(""),
      Some(FacebookFeedInterface.defaultApiEndpoint))

    val eventsVariant = ApiEndpointVariant(
      ApiEndpoint("events", "Facebook events the user has been invited to", None),
      Some(""), Some(""),
      Some(FacebookEventInterface.defaultApiEndpoint))

    val choices = Seq(
      ApiEndpointVariantChoice("profile", "User's Facebook profile information", active = true, profileVariant),
      ApiEndpointVariantChoice("profile/picture", "User's Facebook profile picture", active = true, profilePictureVariant),
      ApiEndpointVariantChoice("feed", "User's Facebook posts feed", active = false, feedVariant),
      ApiEndpointVariantChoice("events", "Facebook events the user has been invited to", active = false, eventsVariant))

    choices
  }

}
