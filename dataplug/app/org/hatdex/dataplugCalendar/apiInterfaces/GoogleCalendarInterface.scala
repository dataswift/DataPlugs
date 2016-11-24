package org.hatdex.dataplugCalendar.apiInterfaces

import akka.actor.ActorRef
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class GoogleCalendarInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val sourceName: String = "google"
  val endpointName: String = "calendar"
  protected val quietTranslationErrors: Boolean = true
  protected val logger: Logger = Logger("GoogleCalendarInterface")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://www.googleapis.com",
    "/calendar/v3/calendars/[calendarId]/events",
    ApiEndpointMethod.Get("Get"),
    Map("calendarId" -> "primary"),
    Map("singleEvents" -> "true"),
    Map())

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

  override protected def processResults(content: JsValue, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Unit] = {
    val kind = (content \ "kind").as[String]
    val summary = (content \ "summary").as[String]
    val description = (content \ "description").asOpt[String]
    val updated = (content \ "updated").asOpt[String]
    val nextPageToken = (content \ "nextPageToken").asOpt[String]
    val nextSyncToken = (content \ "nextSyncToken").asOpt[String]
    val items = (content \ "items").as[List[JsValue]]
    logger.debug(
      s"""Received $kind for $summary ($description):
         | - last updated $updated
         | - ${items.length} items
         | - next page $nextPageToken
         | - next sync $nextSyncToken""".stripMargin)
    Future.successful(())
  }

}
