package org.hatdex.dataplugFitbit.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class FitbitProfileCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FitbitProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger(this.getClass)

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
            val profileVariant = ApiEndpointVariant(
              ApiEndpoint("profile", "User's Fitbit profile information", None),
              Some(""), Some(""),
              Some(FitbitProfileInterface.defaultApiEndpoint)
            )

            val activityVariant = ApiEndpointVariant(
              ApiEndpoint("activity", "User's Fitbit activity list", None),
              Some(""), Some(""),
              Some(FitbitActivityInterface.defaultApiEndpoint)
            )

            val sleepVariant = ApiEndpointVariant(
              ApiEndpoint("sleep", "Fitbit sleep records", None),
              Some(""), Some(""),
              Some(FitbitSleepInterface.defaultApiEndpoint)
            )

            val weightVariant = ApiEndpointVariant(
              ApiEndpoint("weight", "Body weight and BMI measurement", None),
              Some(""), Some(""),
              Some(FitbitWeightInterface.defaultApiEndpoint)
            )

            val lifetimeVariant = ApiEndpointVariant(
              ApiEndpoint("lifetime/stats", "User's Fitbit lifetime statistics", None),
              Some(""), Some(""),
              Some(FitbitLifetimeStatsInterface.defaultApiEndpoint)
            )

            val activitySummaryVariant = ApiEndpointVariant(
              ApiEndpoint("activity/day/summary", "Summary of user's activity throughout the day", None),
              Some(""), Some(""),
              Some(FitbitActivityDaySummaryInterface.defaultApiEndpoint)
            )

            val choices = Seq(
              ApiEndpointVariantChoice("profile", "User's Fitbit profile information", active = true, profileVariant),
              ApiEndpointVariantChoice("activity", "User's Fitbit activity list", active = true, activityVariant),
              ApiEndpointVariantChoice("sleep", "User's Fitbit activity list", active = true, sleepVariant),
              ApiEndpointVariantChoice("weight", "Body weight and BMI measurement", active = true, weightVariant),
              ApiEndpointVariantChoice("lifetime/stats", "User's Fitbit lifetime statistics", active = true, lifetimeVariant),
              ApiEndpointVariantChoice("activity/day/summary", "Summary of user's activity throughout the day", active = true, activitySummaryVariant)
            )

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
