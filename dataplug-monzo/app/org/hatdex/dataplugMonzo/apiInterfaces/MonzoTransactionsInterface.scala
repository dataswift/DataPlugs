package org.hatdex.dataplugMonzo.apiInterfaces

import akka.Done
import akka.actor.{ ActorRef, Scheduler }
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod, ApiEndpointTableStructure }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.{ FutureTransformations, Mailer }
import org.hatdex.dataplugMonzo.apiInterfaces.authProviders.MonzoProvider
import org.hatdex.dataplugMonzo.models.{ MonzoAttachment, MonzoTransaction }
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class MonzoTransactionsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: MonzoProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  // JSON type formatters

  val namespace: String = "monzo"
  val endpoint: String = "transactions"
  protected val logger: Logger = Logger(this.getClass)

  protected val apiEndpointTableStructures: Map[String, ApiEndpointTableStructure] = Map(
    "transactions" -> MonzoTransaction,
    "attachments" -> MonzoAttachment)

  val defaultApiEndpoint = MonzoTransactionsInterface.defaultApiEndpoint

  val refreshInterval = 5.minutes

  /**
   * Builds Monzo Paging API continuation - assumes records ordered by time, oldest first,
   * `since` based on transaction ID
   *
   * @param content Response content
   * @param params API endpoint call paramters used to retrieve the data
   * @returns Optional ApiEndpointCall - None if last page of results has been reached
   */
  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    val maybeTransactions = (content \ "transactions").asOpt[JsArray]

    val maybeLastTransactionId = maybeTransactions flatMap { transactions =>
      if (transactions.value.length == MonzoTransactionsInterface.resultsPerPage) {
        None
      }
      else {
        transactions.value.lastOption.flatMap { transaction =>
          (transaction \ "id").asOpt[String]
        }
      }
    }

    maybeLastTransactionId map { lastTransactionId =>
      val updatedParams = params.queryParameters + ("since" -> lastTransactionId)
      params.copy(queryParameters = updatedParams)
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    val maybeTransactions = (content \ "transactions").asOpt[JsArray]

    val maybeLastTransactionId = maybeTransactions flatMap { transactions =>
      transactions.value.lastOption.flatMap { transaction =>
        (transaction \ "id").asOpt[String]
      }
    }

    maybeLastTransactionId map { lastTransactionId =>
      val updatedParams = params.queryParameters + ("since" -> lastTransactionId)
      params.copy(queryParameters = updatedParams)
    } getOrElse {
      params
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      transactions <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, transactions, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "transactions").toOption.map {
      case data: JsArray if data.validate[List[MonzoTransaction]].isSuccess =>
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

object MonzoTransactionsInterface {
  val resultsPerPage = 100
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.monzo.com",
    "/transactions",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("account_id" -> "account_id", "expand[]" -> "merchant", "limit" -> resultsPerPage.toString),
    Map(),
    Some(Map()))
}
