package org.hatdex.dataplugCalendar.apiInterfaces

import akka.actor.ActorRef
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class GoogleCalendarList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val sourceName: String = "google"
  val endpointName: String = "calendar"
  protected val quietTranslationErrors: Boolean = true
  protected val logger: Logger = Logger("GoogleCalendarInterface")
  val refreshInterval = 0.minutes // irrelevant - never used

  val defaultApiEndpoint = ApiEndpointCall(
    "https://www.googleapis.com",
    "/calendar/v3/users/me/calendarList",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("fields" -> "kind,nextSyncToken,items(id,summary)"),
    Map())

  def get(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[JsValue] = {
    val authenticatedFetchParameters = authenticateRequest(fetchParams, hatAddress)

    authenticatedFetchParameters flatMap { requestParameters =>
      buildRequest(requestParameters)
    } flatMap { result =>
      result.status match {
        case OK =>
          Future.successful(result.json)
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

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = None

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = params

  override protected def processResults(content: JsValue, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Unit] = {
    Future.failed(new RuntimeException("Not Implemented"))
  }

}
