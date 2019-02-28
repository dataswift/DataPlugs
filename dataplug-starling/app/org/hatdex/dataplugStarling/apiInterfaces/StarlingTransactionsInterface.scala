package org.hatdex.dataplugStarling.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplugStarling.apiInterfaces.authProviders.StarlingProvider
import org.hatdex.dataplugStarling.models.StarlingTransaction
import org.joda.time.{ DateTime, DateTimeZone }
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class StarlingTransactionsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: StarlingProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "starling"
  val endpoint: String = "transactions"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint: ApiEndpointCall = StarlingTransactionsInterface.defaultApiEndpoint
  val defaultApiDateFormat: DateTimeFormatter = StarlingTransactionsInterface.defaultApiDateFormat

  val refreshInterval: FiniteDuration = 1.hour

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    (params.queryParameters.get("from"), params.storage.get("to")) match {
      case (None, None) =>
        val toDate = extractLastTimestamp(content).getOrElse(DateTime.now)
        val fromDate = toDate.minusDays(90)

        logger.debug(s"Initial transactions fetch between $fromDate -- $toDate")
        val updatedParams = params.copy(queryParameters = params.queryParameters +
          ("from" -> fromDate.toString(defaultApiDateFormat), "to" -> toDate.toString(defaultApiDateFormat)))

        Some(updatedParams)

      case (Some(from), Some(to)) =>
        val transactionList = Try((content \ "_embedded" \ "transactions").as[JsArray])

        if (transactionList.isSuccess && transactionList.get.value.nonEmpty) {
          val fromDate = DateTime.parse(from, defaultApiDateFormat).minusDays(90)
          val toDate = DateTime.parse(to, defaultApiDateFormat).minusDays(90)

          logger.debug(s"Continuing transactions fetching between $fromDate -- $toDate")
          val updatedParams = params.copy(queryParameters = params.queryParameters +
            ("from" -> fromDate.toString(defaultApiDateFormat), "to" -> toDate.toString(defaultApiDateFormat)))

          Some(updatedParams)
        }
        else {
          logger.debug(s"No more data available - stopping continuation")
          None
        }

      case (Some(_), None) =>
        logger.debug(s"Skipping continuation")
        None
      case _ =>
        logger.error(s"Unidentified continuation state")
        None
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    val fromDate = DateTime.now(DateTimeZone.forID("Europe/London"))

    params.copy(queryParameters = params.queryParameters +
      ("from" -> fromDate.toString(defaultApiDateFormat)) - "to")
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "_embedded" \ "transactions").toOption.map {
      case data: JsArray if data.validate[List[StarlingTransaction]].isSuccess =>
        logger.info(s"Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"Error parsing JSON object: ${data.validate[List[StarlingTransaction]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error obtaining 'items' list: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }

  private def extractLastTimestamp(content: JsValue): Option[DateTime] = {
    (content \ "_embedded" \ "transactions").as[JsArray].value.lastOption.map { lastTransaction =>
      logger.error(s"Last extracted transaction: $lastTransaction")
      DateTime.parse(lastTransaction.as[StarlingTransaction].created)
    }
  }

}

object StarlingTransactionsInterface {
  val defaultApiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api-sandbox.starlingbank.com",
    "/api/v1/transactions",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))
}
