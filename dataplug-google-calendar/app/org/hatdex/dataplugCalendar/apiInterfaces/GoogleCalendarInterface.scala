package org.hatdex.dataplugCalendar.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugCalendar.models._
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class GoogleCalendarInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: GoogleProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  // JSON type formatters
  import GoogleCalendarEventJsonProtocol.eventFormat

  val namespace: String = "google"
  val endpoint: String = "calendar"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint: ApiEndpointCall = GoogleCalendarInterface.defaultApiEndpoint

  val refreshInterval: FiniteDuration = 60.minutes

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    (content \ "nextPageToken").asOpt[String] map { nextPageToken =>
      params.copy(queryParameters = params.queryParameters + ("pageToken" -> nextPageToken))
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
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
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {

    val validatedData = transformData(content, fetchParameters.pathParameters("calendarId")).map(validateMinDataStructure)
      .getOrElse(Failure(SourceDataProcessingException("Source data malformed, could not insert calendar ID in the structure")))

    // Shape results into HAT data records
    val resultsPosted = for {
      validatedData <- FutureTransformations.transform(validatedData) // Parse calendar events into strongly-typed structures
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
    }

    resultsPosted
  }

  private def transformData(rawData: JsValue, calendarId: String) = {
    import play.api.libs.json.Reads._
    import play.api.libs.json._

    val transformation = (__ \ "items").json.update(
      of[JsArray].map {
        case JsArray(arr) => JsArray(
          arr.map { item =>
            item.transform((__ \ 'calendarId).json.put(JsString(calendarId)))
          }.collect {
            case JsSuccess(v, _) => v
          }
        )
      }
    )
    rawData.transform(transformation)
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "items").toOption.map {
      case data: JsArray if data.validate[List[GoogleCalendarEvent]].isSuccess =>
        logger.debug(s"Validated JSON object: ${data.toString}")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing: ${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }

}

object GoogleCalendarInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://www.googleapis.com",
    "/calendar/v3/calendars/[calendarId]/events",
    ApiEndpointMethod.Get("Get"),
    Map("calendarId" -> "primary"),
    Map("singleEvents" -> "true"),
    Map())
}
