package org.hatdex.dataplugFitbit.apiInterfaces

import akka.actor.Scheduler
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class FitbitProfileCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
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
    Map(),
    Some(Map()))

  def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = staticEndpointChoices

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val profileVariant = ApiEndpointVariant(
      ApiEndpoint("profile", "User's Fitbit profile information", None),
      Some(""), Some(""),
      Some(FitbitProfileInterface.defaultApiEndpoint))

    val activityVariant = ApiEndpointVariant(
      ApiEndpoint("activity", "User's Fitbit activity list", None),
      Some(""), Some(""),
      Some(FitbitActivityInterface.defaultApiEndpoint))

    val sleepVariant = ApiEndpointVariant(
      ApiEndpoint("sleep", "Fitbit sleep records", None),
      Some(""), Some(""),
      Some(FitbitSleepInterface.defaultApiEndpoint))

    val weightVariant = ApiEndpointVariant(
      ApiEndpoint("weight", "Body weight and BMI measurement", None),
      Some(""), Some(""),
      Some(FitbitWeightInterface.defaultApiEndpoint))

    val sleepGoalsVariant = ApiEndpointVariant(
      ApiEndpoint("goals/sleep", "User's sleep goals", None),
      Some(""), Some(""),
      Some(FitbitSleepGoalsInterface.defaultApiEndpoint))

    val activityGoalsVariant = ApiEndpointVariant(
      ApiEndpoint("goals/activity", "User's daily activity goals", None),
      Some(""), Some(""),
      Some(FitbitActivityGoalsInterface.defaultApiEndpoint))

    val weightGoalsVariant = ApiEndpointVariant(
      ApiEndpoint("goals/weight", "User's weight goals", None),
      Some(""), Some(""),
      Some(FitbitWeightGoalsInterface.defaultApiEndpoint))

    val fatGoalsVariant = ApiEndpointVariant(
      ApiEndpoint("goals/fat", "User's fat goals", None),
      Some(""), Some(""),
      Some(FitbitFatGoalsInterface.defaultApiEndpoint))

    //    val lifetimeVariant = ApiEndpointVariant(
    //      ApiEndpoint("lifetime/stats", "User's Fitbit lifetime statistics", None),
    //      Some(""), Some(""),
    //      Some(FitbitLifetimeStatsInterface.defaultApiEndpoint))

    val activitySummaryVariant = ApiEndpointVariant(
      ApiEndpoint("activity/day/summary", "Summary of user's activity throughout the day", None),
      Some(""), Some(""),
      Some(FitbitActivityDaySummaryInterface.defaultApiEndpoint))

    val choices = Seq(
      ApiEndpointVariantChoice("profile", "User's Fitbit profile information", active = true, profileVariant),
      ApiEndpointVariantChoice("activity", "User's Fitbit activity list", active = true, activityVariant),
      ApiEndpointVariantChoice("sleep", "User's Fitbit activity list", active = true, sleepVariant),
      ApiEndpointVariantChoice("weight", "Body weight and BMI measurement", active = true, weightVariant),
      ApiEndpointVariantChoice("goals/sleep", "User's sleep goals", active = true, sleepGoalsVariant),
      ApiEndpointVariantChoice("goals/activity", "User's daily activity goals", active = true, activityGoalsVariant),
      ApiEndpointVariantChoice("goals/weight", "User's weight goals", active = true, weightGoalsVariant),
      ApiEndpointVariantChoice("goals/fat", "User's fat goals", active = true, fatGoalsVariant),
      //ApiEndpointVariantChoice("lifetime/stats", "User's Fitbit lifetime statistics", active = true, lifetimeVariant),
      ApiEndpointVariantChoice("activity/day/summary", "Summary of user's activity throughout the day", active = true, activitySummaryVariant))

    choices
  }

}
