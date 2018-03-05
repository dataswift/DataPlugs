package org.hatdex.dataplugCalendar.apiInterfaces

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import org.hatdex.dataplug.apiInterfaces.DataPlugOptionsCollector
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
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
    maybeResponseBody.map { responseBody =>
      (responseBody \ "items").as[Seq[JsValue]] map { calendar =>
        val calendarId = (calendar \ "id").as[String]
        val summary = (calendar \ "summary").as[String]
        val pathParameters = GoogleCalendarInterface.defaultApiEndpoint.pathParameters + ("calendarId" -> calendarId)
        val variant = ApiEndpointVariant(
          ApiEndpoint("google/events", "Google Calendars", None),
          Some(calendarId), Some(summary),
          Some(GoogleCalendarInterface.defaultApiEndpoint.copy(
            pathParameters = pathParameters,
            storageParameters = Some(Map("calendarName" -> summary)))))

        ApiEndpointVariantChoice(calendarId, summary, active = false, variant)
      }
    }.getOrElse(Seq())
  }

}
