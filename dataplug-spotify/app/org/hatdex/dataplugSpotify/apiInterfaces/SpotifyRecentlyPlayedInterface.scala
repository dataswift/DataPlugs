package org.hatdex.dataplugSpotify.apiInterfaces

import akka.Done
import akka.actor.{ ActorRef, Scheduler }
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.utils.FutureTransformations
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugSpotify.apiInterfaces.authProviders.SpotifyProvider
import org.hatdex.dataplugSpotify.models.SpotifyPlayedTrack
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class SpotifyRecentlyPlayedInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: SpotifyProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "spotify"
  val endpoint: String = "feed"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = SpotifyRecentlyPlayedInterface.defaultApiEndpoint

  val refreshInterval = 1.hour

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val nextQueryParams = for {
      nextLink <- Try((content \ "next").as[String]) if nextLink.nonEmpty
      queryParams <- Try(Uri(nextLink).query().toMap) if queryParams.size == 2
    } yield queryParams

    (nextQueryParams, params.storage.get("after")) match {
      case (Success(qp), Some(_)) =>
        logger.debug(s"Next continuation params: $qp")
        Some(params.copy(queryParameters = params.queryParameters ++ qp))

      case (Success(qp), None) =>
        val afterTimestamp = extractAfterTimestamp(content).get
        logger.debug(s"Next continuation params: $qp, setting AfterTimestamp to $afterTimestamp")
        Some(params.copy(queryParameters = params.queryParameters ++ qp, storageParameters = Some(params.storage +
          ("after" -> afterTimestamp))))

      case (Failure(e), _) =>
        logger.debug(s"Next link NOT found. Terminating continuation.")
        None
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    params.storage.get("after").map { afterTimestamp => // After date set (there was at least one continuation step)
      Try((content \ "items").as[JsArray].value) match {
        case Success(_) => // Did continuation but there was no `nextPage` found, if track array present it's a successful completion
          val nextStorage = params.storage - "after"
          val nextQuery = params.queryParameters - "before" + ("after" -> afterTimestamp)
          params.copy(queryParameters = nextQuery, storageParameters = Some(nextStorage))

        case Failure(e) => // Cannot extract tracks array value, most likely an error was returned by the API, continue from where finished previously
          logger.error(s"Provider API request error while performing continuation: $content")
          params
      }
    }.getOrElse { // After date not set, no continuation steps took place
      extractAfterTimestamp(content).map { afterTimestamp => // Extract new after timestamp if there is any content
        val nextQuery = params.queryParameters - "before" + ("after" -> afterTimestamp)
        params.copy(queryParameters = nextQuery)
      }.getOrElse { // There is no content or failed to extract new date
        params
      }
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "items").toOption.map {
      case data: JsArray if data.validate[List[SpotifyPlayedTrack]].isSuccess =>
        logger.info(s"Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"THIS Error parsing JSON object: ${data.validate[List[SpotifyPlayedTrack]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error obtaining 'items' list: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }

  private def extractAfterTimestamp(content: JsValue): Option[String] = {
    Some(DateTime.now.getMillis.toString)
    //    Try((content \ "cursors" \ "after").as[String]) match {
    //      case Success(afterTimestamp) => Some(afterTimestamp)
    //      case Failure(e) =>
    //        logger.error(s"Failed to extract AFTER timestamp.\n Reason: $e")
    //        None
    //    }
  }

}

object SpotifyRecentlyPlayedInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.spotify.com",
    "/v1/me/player/recently-played",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "50"),
    Map(),
    Some(Map()))
}
