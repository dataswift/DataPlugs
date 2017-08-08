package org.hatdex.dataplugFitbit.apiInterfaces

import akka.actor.ActorRef
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class FitbitProfileCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: FitbitProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger("FitbitProfileCheck")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com/1",
    "/user/-/profile.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map())

  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    authenticateRequest(fetchParams, hatAddress).flatMap { requestParams =>
      buildRequest(requestParams).flatMap { response =>
        response.status match {
          case OK =>
            val variant = ApiEndpointVariant(
              ApiEndpoint("dailyActivitySummary", "Summary of user's daily activities", None),
              Some(""), Some(""),
              Some(FitbitDayActivitySummaryInterface.defaultApiEndpoint)
            )

            val choices = Seq(ApiEndpointVariantChoice("dailyActivitySummary", "Summary of user's daily activities", active = true, variant))

            logger.info(s"API endpoint FitbitProfile validated for $hatAddress")
            Future.successful(choices)

          case _ =>
            logger.warn(s"Could not validate FitbitProfile API endpoint $fetchParams - ${response.status}: ${response.body}")
            Future.failed(new RuntimeException("Could not validate FitbitProfile API endpoint"))
        }
      }
    }.recover {
      case e =>
        logger.error(s"Failed to validate FitbitProfile API endpoint. Reason: ${e.getMessage}", e)
        throw e
    }
  }

}
