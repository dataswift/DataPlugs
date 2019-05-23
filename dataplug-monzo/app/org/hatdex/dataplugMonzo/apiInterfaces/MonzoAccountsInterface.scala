package org.hatdex.dataplugMonzo.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplugMonzo.apiInterfaces.authProviders.MonzoProvider
import org.hatdex.dataplugMonzo.models.MonzoAccount
import play.api.Logger
import play.api.libs.json.{ JsArray, JsValue }
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class MonzoAccountsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: MonzoProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  // JSON type formatters

  val namespace: String = "monzo"
  val endpoint: String = "accounts"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = MonzoAccountsInterface.defaultApiEndpoint

  val refreshInterval = 5.minutes

  /**
   * Builds Monzo Paging API continuation - assumes records ordered by time, oldest first,
   * `since` based on transaction ID
   *
   * @param content Response content
   * @param params API endpoint call parameters used to retrieve the data
   * @returns Optional ApiEndpointCall - None if last page of results has been reached
   */
  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    None
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    params
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      transactions <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, transactions, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "accounts").toOption.map {
      case data: JsArray if data.validate[List[MonzoAccount]].isSuccess =>
        logger.debug(s"Validated JSON object:\n${data.toString}")
        Success(data)
      case data: JsArray =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(new RuntimeException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(new RuntimeException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object: ${rawData.toString}")
      Failure(new RuntimeException(s"Error parsing JSON object."))
    }
  }
}

object MonzoAccountsInterface {
  val resultsPerPage = 100
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.monzo.com",
    "/accounts",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("account_id" -> "account_id", "expand[]" -> "merchant", "limit" -> resultsPerPage.toString),
    Map(),
    Some(Map()))
}