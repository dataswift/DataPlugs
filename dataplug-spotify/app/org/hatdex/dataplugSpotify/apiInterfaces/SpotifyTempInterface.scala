package org.hatdex.dataplugSpotify.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugSpotify.apiInterfaces.authProviders.SpotifyProvider
import org.hatdex.dataplugSpotify.models.SpotifyProfile
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter, ISODateTimeFormat }
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class SpotifyTempInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: SpotifyProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "spotify"
  val endpoint: String = "temp"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = SpotifyTempInterface.defaultApiEndpoint

  val refreshInterval = 1.hour

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val nextQueryParams = for {
      nextLink <- Try((content \ "pagination" \ "next").as[String]) if nextLink.nonEmpty
      queryParams <- Try(Uri(nextLink).query().toMap) if queryParams.size == 4
    } yield queryParams

    (nextQueryParams, params.storage.get("afterDate")) match {
      case (Success(qp), Some(_)) =>
        logger.debug(s"Next continuation params: $qp")
        Some(params.copy(queryParameters = params.queryParameters ++ qp))

      case (Success(qp), None) =>
        Some(params)

      case (Failure(e), _) =>
        logger.debug(s"Next link NOT found. Terminating continuation. ${e.getMessage}")
        None
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    params.storage.get("afterDate").map { afterDate => // After date set (there was at least one continuation step)
      Try((content \ "activities").as[JsArray].value) match {
        case Success(_) => // Did continuation but there was no `nextPage` found, if activities array present it's a successful completion
          val nextStorage = params.storage - "afterDate"
          val nextQuery = params.queryParameters - "beforeDate" + ("afterDate" -> afterDate, "offset" -> "0")
          params.copy(queryParameters = nextQuery, storageParameters = Some(nextStorage))

        case Failure(e) => // Cannot extract activities array value, most likely an error was returned by the API, continue from where finished
          logger.error(s"Provider API request error while performing continuation: $content")
          params
      }
    }.getOrElse { // After date not set, no continuation steps took place
      params
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "activities").toOption.map {
      case data: JsArray if data.validate[List[SpotifyProfile]].isSuccess =>
        logger.info(s"Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
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

object SpotifyTempInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.spotify.com",
    "/1/user/-/activities/list.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("sort" -> "desc", "limit" -> "100", "offset" -> "0", "beforeDate" -> "today"),
    Map(),
    Some(Map()))
}
