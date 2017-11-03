package org.hatdex.dataplugCalendar.apiInterfaces

import akka.actor.ActorRef
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import org.hatdex.dataplug.apiInterfaces.{ DataPlugEndpointInterface, DataPlugOptionsCollector }
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpoint, _ }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.hat.api.models.ApiDataTable
import play.api.Logger
import play.api.cache.CacheApi
import play.api.http.Status._
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class GoogleCalendarList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val cacheApi: CacheApi,
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
    Map())

  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Seq[ApiEndpointVariantChoice]] = {
    val authenticatedFetchParameters = authenticateRequest(fetchParams, hatAddress)

    authenticatedFetchParameters flatMap { requestParameters =>
      buildRequest(requestParameters)
    } flatMap { result =>
      result.status match {
        case OK =>
          val choices = (result.json \ "items").as[Seq[JsValue]] map { calendar =>
            val calendarId = (calendar \ "id").as[String]
            val summary = (calendar \ "summary").as[String]
            val pathParameters = GoogleCalendarInterface.defaultApiEndpoint.pathParameters + ("calendarId" -> calendarId)
            val variant = ApiEndpointVariant(
              ApiEndpoint("google/events", "Google Calendars", None),
              Some(calendarId), Some(summary),
              Some(GoogleCalendarInterface.defaultApiEndpoint.copy(pathParameters = pathParameters)))

            ApiEndpointVariantChoice(calendarId, summary, active = false, variant)
          }
          Future.successful(choices)
        case _ =>
          logger.warn(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}")
          Future.failed(new RuntimeException(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}"))
      }
    } recoverWith {
      case e =>
        logger.warn(s"Error when querying api endpoint $fetchParams - ${e.getMessage}")
        Future.failed(e)
    }
  }

}
