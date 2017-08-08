package org.hatdex.dataplugFitbit.apiInterfaces

import akka.actor.ActorRef
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod, ApiEndpointTableStructure }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import org.hatdex.dataplugFitbit.models.{ FitbitDayActivitySummary, FitbitActivity }
import org.hatdex.hat.api.models.{ ApiDataRecord, ApiDataTable }
import org.joda.time.DateTime
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FitbitDayActivitySummaryInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val provider: FitbitProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  // JSON type formatters

  val namespace: String = "fitbit"
  val endpoint: String = "dailyActivitySummary"
  protected val logger: Logger = Logger("FitbitDayActivitySummaryInterface")

  protected val apiEndpointTableStructures: Map[String, ApiEndpointTableStructure] = Map(
    "dayActivitySummaries" -> FitbitDayActivitySummary,
    "activities" -> FitbitActivity)

  val defaultApiEndpoint = FitbitDayActivitySummaryInterface.defaultApiEndpoint

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
    None
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
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {

    for {
      dayActivitySummary <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, dayActivitySummary, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    rawData match {
      case data: JsArray if data.validate[List[FitbitDayActivitySummary]].isSuccess =>
        logger.debug(s"Validated JSON object:\n${data.toString}")
        Success(data)
      case data: JsArray =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(new RuntimeException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(new RuntimeException(s"Error parsing JSON object."))
    }
  }

}

object FitbitDayActivitySummaryInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com",
    "/1/user/-/activities/date/[date].json",
    ApiEndpointMethod.Get("Get"),
    Map("date" -> "2016-12-09"),
    Map(),
    Map())
}
