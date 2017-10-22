package org.hatdex.dataplugFitbit.apiInterfaces

import java.util.UUID

import akka.Done
import akka.actor.Scheduler
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugApiEndpointClient
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models._
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class FitbitSubscription @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FitbitProvider) extends DataPlugApiEndpointClient with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "subscription"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com/1",
    "/user/-/apiSubscriptions/[collection-path]/[subscription].json",
    ApiEndpointMethod.Post("Post", ""),
    Map(),
    Map(),
    Map())

  def create(collectionPath: String, hatAddress: String)(implicit ec: ExecutionContext): Future[Done] = {
    val params = defaultApiEndpoint.copy(pathParameters =
      Map("collection-path" -> collectionPath, "subscription" -> UUID.randomUUID().toString))

    authenticateRequest(params, hatAddress, refreshToken = false).flatMap { requestParams =>
      buildRequest(requestParams).map { response =>
        response.status match {
          case OK =>
            logger.info(s"Fitbit API subscription added: ${response.body}")
            Done

          case _ =>
            logger.warn(s"Could not add Fitbit subscription with $params - ${response.status}: ${response.body}")
            throw SourceDataProcessingException(s"Could not add Fitbit subscription with $params - ${response.status}")
        }
      }
    }
  }

  def delete(collectionPath: String, hatAddress: String)(implicit ec: ExecutionContext): Future[Done] = {
    // FIXME: need to delete a specific subscription ID!
    val params = defaultApiEndpoint.copy(
      pathParameters =
      Map("collection-path" -> collectionPath, "subscription" -> UUID.randomUUID().toString),
      method = ApiEndpointMethod.Delete("Delete"))

    authenticateRequest(params, hatAddress, refreshToken = false).flatMap { requestParams =>
      buildRequest(requestParams).map { response =>
        response.status match {
          case OK =>
            logger.info(s"Fitbit API subscription deleted: ${response.body}")
            Done

          case _ =>
            logger.warn(s"Could not delete Fitbit subscription with $params - ${response.status}: ${response.body}")
            throw SourceDataProcessingException(s"Could not delete Fitbit subscription with $params - ${response.status}")
        }
      }
    }
  }

}
