package org.hatdex.dataplugFacebook.apiInterfaces

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider
import org.hatdex.dataplug.actors.Errors.SourceApiCommunicationException
import org.hatdex.dataplug.apiInterfaces.DataPlugContentUploader
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod, DataPlugNotableShareRequest }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFacebook.models.FacebookFeedUpdate
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class FacebookFeedUploadInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val provider: FacebookProvider) extends DataPlugContentUploader with RequestAuthenticatorOAuth2 {

  protected val logger: Logger = Logger(this.getClass)
  val defaultApiEndpoint = FacebookFeedUploadInterface.defaultApiEndpoint
  val photoUploadApiEndpoint = FacebookFeedUploadInterface.photoUploadApiEndpoint
  val deleteApiEndpoint = FacebookFeedUploadInterface.deleteApiEndpoint
  val namespace: String = "facebook"
  val endpoint: String = "feed"

  def post(hatAddress: String, content: DataPlugNotableShareRequest)(implicit ec: ExecutionContext): Future[FacebookFeedUpdate] = {
    logger.info(s"Posting to Facebook for $hatAddress")

    val apiEndpoint = if (content.photo.isDefined) {
      logger.debug(s"Found photo. Uploading to Facebook, caption:\n${content.message}\n photo:\n${content.photo.get}")
      photoUploadApiEndpoint.copy(
        method = ApiEndpointMethod.Post("Post", Json.stringify(Json.obj(
          "caption" -> content.message,
          "url" -> content.photo.get))))
    }
    else {
      logger.debug(s"Found message. Posting to Facebook:\n${content.message}")
      defaultApiEndpoint.copy(
        method = ApiEndpointMethod.Post("Post", Json.stringify(Json.obj("message" -> content.message))))
    }

    val authenticatedApiEndpoint = authenticateRequest(apiEndpoint, hatAddress)

    authenticatedApiEndpoint flatMap { requestParams =>
      buildRequest(requestParams) flatMap { result =>
        result.status match {
          case OK =>
            Future.successful(Json.parse(result.body).as[FacebookFeedUpdate])
          case status =>
            Future.failed(SourceApiCommunicationException(
              s"Unexpected response from facebook (status code $status): ${result.body}"))
        }
      }
    }
  }

  def delete(hatAddress: String, providerId: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Deleting shared facebook post $providerId for $hatAddress.")

    val authenticatedApiCall = authenticateRequest(deleteApiEndpoint.copy(pathParameters = Map("post-id" -> providerId)), hatAddress)

    authenticatedApiCall flatMap { requestParams =>
      buildRequest(requestParams) flatMap { result =>
        result.status match {
          case OK =>
            Future.successful(Unit)
          case status =>
            Future.failed(SourceApiCommunicationException(s"Unexpected response from facebook when deleting post $providerId (status code $status): ${result.body}"))
        }
      }
    }
  }
}

object FacebookFeedUploadInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v2.10",
    "/me/feed",
    ApiEndpointMethod.Post("Post", ""),
    Map(),
    Map(),
    Map("Content-Type" -> "application/json"))

  val photoUploadApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v2.10",
    "/me/photos",
    ApiEndpointMethod.Post("Post", ""),
    Map(),
    Map(),
    Map("Content-Type" -> "application/json"))

  val deleteApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v2.10",
    "/[post-id]",
    ApiEndpointMethod.Delete("Delete"),
    Map(),
    Map(),
    Map())
}
