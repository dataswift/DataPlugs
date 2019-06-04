package org.hatdex.dataplugUber.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplugUber.apiInterfaces.authProviders.UberProvider
import org.hatdex.dataplugUber.models.UberRide
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class UberRidesHistoryInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: UberProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  // JSON type formatters

  val namespace: String = "monzo"
  val endpoint: String = "history"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint: ApiEndpointCall = UberRidesHistoryInterface.defaultApiEndpoint

  val refreshInterval: FiniteDuration = 1.day

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    val maybeCount = (content \ "count").asOpt[Int]
    val maybeLastRideSynced = params.storage.get("lastSyncedRide")

    (content \ "history").asOpt[Seq[UberRide]] flatMap { rides =>
      maybeCount.flatMap { count =>
        if (count > 50) {
          val previousOffset = params.queryParameters.get("offset").getOrElse("0").toInt
          maybeLastRideSynced.map { lastSyncedRequestId =>

            val test = rides.find(_.request_id == lastSyncedRequestId).isDefined
            Some(params.copy(queryParameters = params.queryParameters + ("offset" -> (previousOffset + rides.length).toString)))
          }.getOrElse(Some(params.copy(queryParameters = params.queryParameters + ("offset" -> (previousOffset + rides.length).toString))))
        }
        else {
          None
        }
      }
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    val count = (content \ "count").asOpt[Int].getOrElse(0)
    val offset = (content \ "offset").asOpt[Int].getOrElse(0)

    if (offset != count) {
      val updatedParams = params.queryParameters - "offset"
      params.copy(queryParameters = updatedParams)
    }
    else {
      params
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

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
    (rawData \ "history").toOption.map {
      case data: JsArray if data.validate[List[UberRide]].isSuccess =>
        logger.debug(s"Validated JSON object: ${data.value.length}")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing: ${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"Error parsing JSON object: ${data.toString} ${data.validate[List[UberRide]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }

}

object UberRidesHistoryInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.uber.com",
    "/v1.2/history",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "50"),
    Map(),
    Some(Map()))
}
