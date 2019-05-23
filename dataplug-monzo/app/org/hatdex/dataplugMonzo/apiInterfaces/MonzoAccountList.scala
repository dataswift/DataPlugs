package org.hatdex.dataplugMonzo.apiInterfaces

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugMonzo.apiInterfaces.authProviders.MonzoProvider
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class MonzoAccountList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: MonzoProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "monzo"
  val endpoint: String = "accounts"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.monzo.com",
    "/accounts",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))

  def generateEndpointChoices(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    staticEndpointChoices ++ generateAccountsEndpointChoices(maybeResponseBody)
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val accountsVariant = ApiEndpointVariant(
      ApiEndpoint("accounts", "User's monzo accounts", None),
      Some(""), Some(""),
      Some(defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("accounts", "User's monzo accounts", active = true, accountsVariant))
  }

  def generateAccountsEndpointChoices(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    maybeResponseBody.map { responseBody =>
      (responseBody \ "accounts").as[Seq[JsValue]] map { account =>
        val accountId = (account \ "id").as[String]
        val description = (account \ "description").as[String]
        val queryParameters = MonzoTransactionsInterface.defaultApiEndpoint.queryParameters + ("account_id" -> accountId)
        val variant = ApiEndpointVariant(
          ApiEndpoint("transactions", "Monzo Transactions", None),
          Some(accountId), Some(description),
          Some(MonzoTransactionsInterface.defaultApiEndpoint.copy(queryParameters = queryParameters)))

        ApiEndpointVariantChoice(accountId, description, active = false, variant)
      }
    }.getOrElse(Seq())
  }

}
