package org.hatdex.dataplugMonzo.apiInterfaces

import akka.actor.ActorRef
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugMonzo.apiInterfaces.authProviders.MonzoProvider
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class MonzoAccountList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: MonzoProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "monzo"
  val endpoint: String = "accounts"
  protected val logger: Logger = Logger("MonzoTransactionsInterface")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.monzo.com",
    "/accounts",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map())

  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    val authenticatedFetchParameters = authenticateRequest(fetchParams, hatAddress)

    authenticatedFetchParameters flatMap { requestParameters =>
      buildRequest(requestParameters)
    } flatMap { result =>
      result.status match {
        case OK =>
          logger.info(s"Found monzo accounts: ${result.json}")
          val choices = (result.json \ "accounts").as[Seq[JsValue]] map { account =>
            val accountId = (account \ "id").as[String]
            val description = (account \ "description").as[String]
            val queryParameters = MonzoTransactionsInterface.defaultApiEndpoint.queryParameters + ("account_id" -> accountId)
            val variant = ApiEndpointVariant(
              ApiEndpoint("transactions", "Monzo Transactions", None),
              Some(accountId), Some(description),
              Some(MonzoTransactionsInterface.defaultApiEndpoint.copy(queryParameters = queryParameters)))

            ApiEndpointVariantChoice(accountId, description, active = false, variant)
          }
          Future.successful(choices)
        case _ =>
          logger.warn(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}")
          Future.failed(new RuntimeException(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}"))
      }
    } recoverWith {
      case e =>
        logger.warn(s"Error when querying api endpoint $fetchParams - ${e.getMessage}")
        Future.failed(e)
    }
  }

}
