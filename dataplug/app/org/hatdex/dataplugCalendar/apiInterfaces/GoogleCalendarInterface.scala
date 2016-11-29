package org.hatdex.dataplugCalendar.apiInterfaces

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
import org.hatdex.dataplugCalendar.models._
import org.hatdex.hat.api.models.{ ApiDataRecord, ApiDataTable }
import org.joda.time.DateTime
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
    val mailer: Mailer) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  // JSON type formatters
  import GoogleCalendarEventJsonProtocol.eventFormat
  import GoogleCalendarEventJsonProtocol.attendeeFormat

  val sourceName: String = "google"
  val endpointName: String = "calendar"
  protected val logger: Logger = Logger("GoogleCalendarInterface")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://www.googleapis.com",
    "/calendar/v3/calendars/[calendarId]/events",
    ApiEndpointMethod.Get("Get"),
    Map("calendarId" -> "primary"),
    Map("singleEvents" -> "true"),
    Map())

  protected val apiEndpointTableStructures: Map[String, ApiEndpointTableStructure] = Map(
    "events" -> GoogleCalendarEvent,
    "attendees" -> GoogleCalendarAttendee
  )

  //  val refreshInterval = 5.minutes
  val refreshInterval = 1.minutes

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

    val items = (content \ "items").as[JsArray] // Calendar items returned by the API call
    val calendarId = fetchParameters.pathParameters("calendarId") // Get calendar ID to be attached to all events

    // Shape results into HAT data records
    val resultsPosted = for {
      events <- FutureTransformations.transform(parseEvents(items, calendarId)) // Parse calendar events into strongly-typed structures
      tableStructures <- ensureDataTables(hatAddress, hatClientActor) // Ensure HAT data tables have been created
      apiDataRecords <- Future.sequence(events.map(convertGoogleEventToHat(_, tableStructures)))
      posted <- uploadHatData(apiDataRecords, hatAddress, hatClientActor) // Upload the data
    } yield {
      debug(content, events)
      posted
    }

    resultsPosted
  }

  private def convertGoogleEventToHat(event: GoogleCalendarEvent, tableStructures: Map[String, ApiDataTable]): Future[ApiDataRecord] = {
    val plainDataForRecords = buildJsonRecord(event)
    val recordTimestamp = event.updated.orElse(event.created).map(t => new DateTime(t))
    buildHatDataRecord(plainDataForRecords, sourceName, event.id, recordTimestamp, tableStructures)
  }

  private def debug(content: JsValue, events: Seq[GoogleCalendarEvent]): Unit = {
    // Calendar information returned by the API call
    val kind = (content \ "kind").as[String]
    val summary = (content \ "summary").as[String]
    val description = (content \ "description").asOpt[String]
    val updated = (content \ "updated").asOpt[String]
    val nextPageToken = (content \ "nextPageToken").asOpt[String]
    val nextSyncToken = (content \ "nextSyncToken").asOpt[String]

    logger.debug(
      s"""Received $kind for $summary ($description):
          | - last updated $updated
          | - ${events.length} items
          | - next page $nextPageToken
          | - next sync $nextSyncToken""".stripMargin)
  }

  private def parseEvents(items: JsArray, calendarId: String): Try[List[GoogleCalendarEvent]] = {
    items.validate[List[GoogleCalendarEvent]] match {
      case s: JsSuccess[List[GoogleCalendarEvent]] =>
        val events = s.get.map { e =>
          // make sure all attendees contain both the event id and calendar id
          val eAttendees = e.attendees.map(_.map(_.copy(eventId = Some(e.id), Some(calendarId))))
          e.copy(calendarId = Some(calendarId), attendees = eAttendees)
        }

        Success(events)
      case e: JsError =>
        val error = new RuntimeException(s"Error parsing event values: $e")
        logger.error(s"Error parsing event values: $e - ${items.toString()}")
        Failure(error)
    }
  }

  private def buildJsonRecord(event: GoogleCalendarEvent): JsArray = {
    val eventWithoutAttendeesJson = JsObject(Map("events" -> Json.toJson(event.copy(attendees = None))))

    val eventAttendees = event.attendees
      .map(atts => atts.map(a => Json.toJson(a)))
      .getOrElse(List())
      .map(e => JsObject(Map("attendees" -> e)))

    // Create JsArray with event and attendee objects
    val eventRecord = JsArray(eventAttendees) :+ eventWithoutAttendeesJson

    logger.debug(s"Events: ${Json.prettyPrint(eventRecord)}")

    eventRecord
  }

}
