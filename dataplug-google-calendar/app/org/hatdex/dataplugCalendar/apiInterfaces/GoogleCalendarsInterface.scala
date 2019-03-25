package org.hatdex.dataplugCalendar.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplugCalendar.models.GoogleCalendar
import org.hatdex.dataplugCalendar.models.GoogleCalendarJsonProtocol._
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject, JsResult, JsValue }
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class GoogleCalendarsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: GoogleProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "calendar"
  val endpoint: String = "google/calendars"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint: ApiEndpointCall = GoogleCalendarsInterface.defaultApiEndpoint

  val refreshInterval: FiniteDuration = 7.days

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation")
    (content \ "nextPageToken").asOpt[String] map { nextPageToken =>
      params.copy(queryParameters = params.queryParameters + ("pageToken" -> nextPageToken))
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug("Building next sync")
    val maybeNextSyncToken = (content \ "nextSyncToken").asOpt[String]

    maybeNextSyncToken map { nextSyncToken =>
      val updatedParams = params.queryParameters + ("syncToken" -> nextSyncToken) - "pageToken"
      params.copy(queryParameters = updatedParams)
    } getOrElse {
      params
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    logger.debug("processing results")

    val validatedData: Try[JsArray] = validateMinDataStructure(content)

    // Shape results into HAT data records
    val resultsPosted = for {
      validatedData <- FutureTransformations.transform(validatedData) // Parse calendar events into strongly-typed structures
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }

    resultsPosted
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "items").toOption.map {
      case data: JsArray if data.validate[List[GoogleCalendar]].isSuccess =>
        logger.debug(s"Validated JSON object: ${data.value.length}")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing: ${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"Error parsing JSON object: ${data.toString} ${data.validate[List[GoogleCalendar]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }
}

object GoogleCalendarsInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://www.googleapis.com",
    "/calendar/v3/users/me/calendarList",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("fields" -> "kind,nextSyncToken,items(id,summary)"),
    Map(),
    Some(Map()))
}
