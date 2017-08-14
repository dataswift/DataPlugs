package org.hatdex.dataplugFitbit.apiInterfaces

import akka.actor.ActorRef
import akka.http.scaladsl.model.Uri
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
import org.hatdex.dataplugFitbit.models.FitbitActivity
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter, ISODateTimeFormat }
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FitbitActivityInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val provider: FitbitProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "activity"
  protected val logger: Logger = Logger("FitbitActivityInterface")

  val defaultApiEndpoint = FitbitActivityInterface.defaultApiEndpoint

  val refreshInterval = 10.minutes

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
      dayActivitySummary <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, dayActivitySummary, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "activities").toOption.map {
      case data: JsArray if data.validate[List[FitbitActivity]].isSuccess =>
        logger.debug(s"Validated JSON object:\n${data.toString}")
        Success(data)
      case data: JsObject =>
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

  private def extractNextSyncTimestamp(content: JsValue): Option[String] = {
    val maybeFirstActivityTimestamp = (content \ "activities").asOpt[JsArray].flatMap { activityList =>
      activityList.value.headOption.flatMap { firstActivity => (firstActivity \ "startTime").asOpt[String] }
    }

    maybeFirstActivityTimestamp.map { firstActivityTimestamp =>
      val timestamp = ISODateTimeFormat.dateTimeParser().parseDateTime(firstActivityTimestamp)
      val afterDate = timestamp.plusSeconds(1)
      afterDate.toString(FitbitActivityInterface.apiDateFormat)
    }
  }

}

object FitbitActivityInterface {
  val apiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com",
    "/1/user/-/activities/list.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("sort" -> "desc", "limit" -> "2", "offset" -> "0", "beforeDate" -> "today"),
    Map())
}
