package org.hatdex.dataplugFitbit.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import org.hatdex.dataplugFitbit.models.FitbitSleep
import org.joda.time.DateTime
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter, ISODateTimeFormat }
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FitbitSleepInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FitbitProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "sleep"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = FitbitSleepInterface.defaultApiEndpoint
  val defaultApiDateFormat = FitbitSleepInterface.apiDateFormat

  val cutoffDate = DateTime.parse("2017-01-01", defaultApiDateFormat)

  val refreshInterval = 24.hours

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    (
      params.pathParameters.get("baseDate"),
      params.pathParameters.get("endDate"),
      params.storageParameters.get("earliestSyncedDate")) match {
        case (Some(baseDateStr), Some(endDateStr), Some(earliestSyncedDateStr)) =>
          val baseDate = DateTime.parse(baseDateStr, defaultApiDateFormat)
          val earliestSyncedDate = DateTime.parse(earliestSyncedDateStr, defaultApiDateFormat)

          if (baseDateStr == endDateStr && earliestSyncedDate.isAfter(cutoffDate)) {
            Some(params.copy(
              pathParameters = params.pathParameters +
                ("baseDate" -> earliestSyncedDate.minusDays(99).toString(defaultApiDateFormat),
                  "endDate" -> earliestSyncedDate.minusDays(1).toString(defaultApiDateFormat))))
          }
          else if (earliestSyncedDate.isAfter(cutoffDate)) {
            Some(params.copy(
              pathParameters = params.pathParameters +
                ("baseDate" -> baseDate.minusDays(99).toString(defaultApiDateFormat),
                  "endDate" -> baseDate.minusDays(1).toString(defaultApiDateFormat)),
              storageParameters = params.storageParameters + ("earliestSyncedDate" -> baseDateStr)))
          }
          else {
            None
          }
        case (_, _, _) => None
      }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    params.copy(pathParameters = params.pathParameters +
      ("baseDate" -> DateTime.now.toString(defaultApiDateFormat),
        "endDate" -> DateTime.now.toString(defaultApiDateFormat)))
  }

  override def buildFetchParameters(params: Option[ApiEndpointCall])(implicit ec: ExecutionContext): Future[ApiEndpointCall] = {
    logger.debug(s"Custom building fetch params: \n $params")

    val finalFetchParams = params.map { p =>
      p.pathParameters.get("baseDate").map { _ => p }.getOrElse {
        val updatedPathParams = p.pathParameters +
          ("baseDate" -> DateTime.now.minusDays(99).toString(defaultApiDateFormat),
            "endDate" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))
        val updatedStorageParams = p.storageParameters + ("earliestSyncedDate" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))

        p.copy(pathParameters = updatedPathParams, storageParameters = updatedStorageParams)
      }
    }.getOrElse {
      val updatedPathParams = defaultApiEndpoint.pathParameters +
        ("baseDate" -> DateTime.now.minusDays(99).toString(defaultApiDateFormat),
          "endDate" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))
      val updatedStorageParams = defaultApiEndpoint.storageParameters + ("earliestSyncedDate" -> DateTime.now.minusDays(1).toString(defaultApiDateFormat))
      defaultApiEndpoint.copy(pathParameters = updatedPathParams, storageParameters = updatedStorageParams)
    }

    logger.debug(s"Final fetch parameters: \n $finalFetchParams")

    Future.successful(finalFetchParams)
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "sleep").toOption.map {
      case data: JsArray if data.validate[List[FitbitSleep]].isSuccess =>
        logger.debug(s"Validated JSON object with ${data.value.length} values.")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object, necessary property not found: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object, necessary property not found."))
    }
  }

}

object FitbitSleepInterface {
  val apiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com",
    "/1.2/user/-/sleep/date/[baseDate]/[endDate].json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Map())
}
