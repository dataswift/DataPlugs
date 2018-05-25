package org.hatdex.dataplugStarling.apiInterfaces

import akka.actor.Scheduler
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugStarling.apiInterfaces.authProviders.StarlingProvider
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class StarlingProfileCheck @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: StarlingProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "starling"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api-sandbox.starlingbank.com",
    "/api/v2/account-holder/individual",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def generateEndpointChoices(responseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = staticEndpointChoices

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val profileVariant = ApiEndpointVariant(
      ApiEndpoint("account-holder", "Starling account holder's profile information", None),
      Some(""), Some(""),
      Some(StarlingProfileInterface.defaultApiEndpoint))

    val transactionsVariant = ApiEndpointVariant(
      ApiEndpoint("transactions", "A list of transactions associated with the account holder", None),
      Some(""), Some(""),
      Some(StarlingTransactionsInterface.defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("account-holder", "Starling account holder's profile information", active = true, profileVariant),
      ApiEndpointVariantChoice("transactions", "A list of transactions associated with the account holder", active = true, transactionsVariant))
  }

}
