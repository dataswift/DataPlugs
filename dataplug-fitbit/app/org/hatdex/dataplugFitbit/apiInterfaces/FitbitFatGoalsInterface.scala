package org.hatdex.dataplugFitbit.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import org.hatdex.dataplugFitbit.models.FitbitFatGoal
import org.joda.time.DateTime
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject, JsValue }
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FitbitFatGoalsInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FitbitProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "fitbit"
  val endpoint: String = "goals/fat"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = FitbitFatGoalsInterface.defaultApiEndpoint

  val refreshInterval = 7.days

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    None
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    params
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClient: AuthenticatedHatClient,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    val transformedData = transformData(content).getOrElse(JsObject(Map.empty[String, JsValue]))

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(transformedData))
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  private def transformData(rawData: JsValue): Option[JsObject] = {
    import play.api.libs.json._

    val transformation = (__ \ "goal").json.update(
      __.read[JsObject].map(o => o ++ JsObject(Map("hatUpdatedTime" -> JsString(DateTime.now.toString)))))

    (rawData \ "goal").asOpt[JsObject] match {
      case Some(value) if value.values.nonEmpty => value.transform(transformation).asOpt
      case _                                    => None
    }
  }

  override def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "goal").toOption.map {
      case data: JsValue if data.validate[FitbitFatGoal].isSuccess =>
        logger.info(s"Validated JSON for fitbit fat goal.")
        Success(JsArray(Seq(data)))
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      rawData.asOpt[JsObject] match {
        case Some(value) if value.values.isEmpty =>
          logger.info(s"Error validating data, value was empty:\n${value.toString}")
          Success(JsArray(Seq()))

        case _ =>
          logger.error(s"Error parsing JSON object, necessary property not found: ${rawData.toString}")
          Failure(SourceDataProcessingException(s"Error parsing JSON object, necessary property not found."))
      }
    }
  }

}

object FitbitFatGoalsInterface {
  val apiDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")

  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.fitbit.com",
    "/1.2/user/-/body/log/fat/goal.json",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map(),
    Map(),
    Some(Map()))
}
