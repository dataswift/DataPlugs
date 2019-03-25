package org.hatdex.dataplugCalendar.apiInterfaces

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugCalendar.models.GoogleCalendar
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class GoogleCalendarList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: GoogleProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "calendar"
  val endpoint: String = "google/events"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://www.googleapis.com",
    "/calendar/v3/users/me/calendarList",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("fields" -> "kind,nextSyncToken,items(id,summary)"),
    Map(),
    Some(Map()))

  def generateEndpointChoices(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    staticEndpointChoices ++ generateCalendarEventsEndpoints(maybeResponseBody)
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val calendarsVariant = ApiEndpointVariant(
      ApiEndpoint("google/calendars", "User's google calendars", None),
      Some(""), Some(""),
      Some(GoogleCalendarsInterface.defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("google/calendars", "User's google calendars", active = true, calendarsVariant))
  }

  private def generateCalendarEventsEndpoints(maybeResponseBody: Option[JsValue]) = {
    import org.hatdex.dataplugCalendar.models.GoogleCalendarJsonProtocol._

    maybeResponseBody.map { responseBody =>
      (responseBody \ "items").as[Seq[GoogleCalendar]] map { calendar =>
        val pathParameters = GoogleCalendarEventsInterface.defaultApiEndpoint.pathParameters + ("calendarId" -> calendar.id)
        val variant = ApiEndpointVariant(
          ApiEndpoint("google/events", "Google Calendars", None),
          Some(calendar.id), Some(calendar.summary),
          Some(GoogleCalendarEventsInterface.defaultApiEndpoint.copy(
            pathParameters = pathParameters,
            storageParameters = Some(Map("calendarName" -> calendar.summary)))))

        ApiEndpointVariantChoice(calendar.id, calendar.summary, active = false, variant)
      }
    }.getOrElse(Seq())
  }

}
