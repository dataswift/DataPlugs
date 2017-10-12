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

  val refreshInterval = 24.hours

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")
    val continuationPathParams = params.pathParameters.get("nextSyncTimestamp").map { timestamp =>
      logger.debug(s"Next Sync Timestamp found: $timestamp")
      params.pathParameters
    }.getOrElse {
      extractNextSyncTimestamp(content).map { nextSyncTimestamp =>
        logger.debug(s"Next Sync Timestamp NOT found. Setting it to $nextSyncTimestamp")
        params.pathParameters + ("nextSyncTimestamp" -> nextSyncTimestamp)
      }.getOrElse {
        logger.debug(s"Next Sync Timestamp NOT found and CANNOT be set!")
        params.pathParameters
      }
    }

    val nextLink = (content \ "pagination" \ "next").as[String]

    if (nextLink.nonEmpty) {
      logger.debug(s"Next link found: $nextLink")
      val maybeOffset = Uri(nextLink).queryString().flatMap { queryString =>
        queryString.split("&").find(_.contains("offset")).flatMap(_.split("=").lastOption)
      }

      maybeOffset.map { offset =>
        logger.debug(s"Setting offset to: $offset")
        logger.debug(s"Next continuation Params: ${params.copy(pathParameters = continuationPathParams, queryParameters = params.queryParameters + ("offset" -> offset))}")
        params.copy(
          pathParameters = continuationPathParams,
          queryParameters = params.queryParameters + ("offset" -> offset))
      }
    }
    else {
      logger.debug(s"Next link NOT found. Terminating continuation.")
      None
    }

  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")
    params.pathParameters.get("nextSyncTimestamp").map { nextSyncTimestamp =>
      val nextPathParameters = params.pathParameters - "nextSyncTimestamp"
      val nextQueryParameters = params.queryParameters - "beforeDate" + ("afterDate" -> nextSyncTimestamp, "offset" -> "0")
      logger.debug(s"Next sync params: ${params.copy(pathParameters = nextPathParameters, queryParameters = nextQueryParameters)}")
      params.copy(pathParameters = nextPathParameters, queryParameters = nextQueryParameters)
    }.getOrElse {
      logger.debug(s"No continuation built. Updating query parameters...")

      extractNextSyncTimestamp(content).map { nextSyncTimestamp =>
        val nextQueryParameters = params.queryParameters - "beforeDate" + ("afterDate" -> nextSyncTimestamp)
        logger.debug(s"New data added. Updating query params to $nextQueryParameters")
        params.copy(queryParameters = nextQueryParameters)
      }.getOrElse {
        logger.debug("Nothing to update. Leaving query params as is.")
        params
      }
    }
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
        logger.debug(s"Validated JSON object:\n${data.toString}")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }

  private def extractNextSyncTimestamp(content: JsValue): Option[String] = {
    val maybeFirstActivityTimestamp = (content \ "sleep").asOpt[JsArray].flatMap { activityList =>
      activityList.value.headOption.flatMap { firstActivity => (firstActivity \ "endTime").asOpt[String] }
    }

    maybeFirstActivityTimestamp.map { firstActivityTimestamp =>
      val timestamp = ISODateTimeFormat.dateTimeParser().parseDateTime(firstActivityTimestamp)
      val afterDate = timestamp.plusSeconds(1)
      afterDate.toString(FitbitSleepInterface.apiDateFormat)
    }
  }

}

object FitbitSleepInterface {
  val apiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com",
    "/1.2/user/-/sleep/list.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("sort" -> "desc", "limit" -> "2", "offset" -> "0", "beforeDate" -> "today"),
    Map())
}
