package org.hatdex.dataplugUber.apiInterfaces

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugUber.apiInterfaces.authProviders.UberProvider
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

class UberList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: UberProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "monzo"
  val endpoint: String = "history"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.uber.com",
    "/v1.2/history",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def generateEndpointChoices(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    staticEndpointChoices
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val profileVariant = ApiEndpointVariant(
      ApiEndpoint("uberprofile", "User's uber profile", None),
      Some(""), Some(""),
      Some(UberProfileInterface.defaultApiEndpoint))

    val historyVariant = ApiEndpointVariant(
      ApiEndpoint("history", "User's uber history", None),
      Some(""), Some(""),
      Some(UberRidesHistoryInterface.defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("uberprofile", "User's uber profile", active = true, profileVariant),
      ApiEndpointVariantChoice("history", "User's uber history", active = true, historyVariant))
  }

}
