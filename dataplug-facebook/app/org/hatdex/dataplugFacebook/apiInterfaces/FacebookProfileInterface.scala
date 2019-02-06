package org.hatdex.dataplugFacebook.apiInterfaces

import akka.Done
import akka.actor.Scheduler
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider
import org.joda.time.DateTime
import org.hatdex.dataplug.utils.{ AuthenticatedHatClient, FutureTransformations, Mailer }
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplugFacebook.models.FacebookProfile
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FacebookProfileInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FacebookProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "facebook"
  val endpoint: String = "profile"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = FacebookProfileInterface.defaultApiEndpoint

  val refreshInterval = 24.hours

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

    val dataValidation =
      transformData(content)
        .map(tempObject => validateMinDataStructure(tempObject, hatAddress))
        .getOrElse(Failure(SourceDataProcessingException(s"[$hatAddress] Source data malformed, could not insert date in to the structure")))

    for {
      validatedData <- FutureTransformations.transform(dataValidation)
      _ <- uploadHatData(namespace, endpoint, validatedData, hatAddress, hatClient) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  private def transformData(rawData: JsValue): JsResult[JsObject] = {
    import play.api.libs.json._

    val transformation = __.json.update(
      __.read[JsObject].map(profile => {
        val friends = (profile \ "friends" \ "data").asOpt[JsArray].getOrElse(JsArray())
        val friendCount = (profile \ "friends" \ "summary" \ "total_count").asOpt[JsNumber].getOrElse(JsNumber(0))
        val ageMin = (profile \ "age_range" \ "min").asOpt[Int]
        val ageMax = (profile \ "age_range" \ "max").asOpt[Int]

        profile ++ JsObject(Map(
          "friends" -> friends,
          "hat_updated_time" -> JsString(DateTime.now.toString),
          "friend_count" -> friendCount,
          "age_range" -> JsString(s"${ageMin.getOrElse("unknown")} - ${ageMax.getOrElse("unknown")}")))
      }))

    rawData.transform(transformation)
  }

  def validateMinDataStructure(rawData: JsValue, hatAddress: String): Try[JsArray] = {
    rawData match {
      case data: JsObject if data.validate[FacebookProfile].isSuccess =>
        logger.info(s"[$hatAddress] Validated JSON facebook profile object.")
        Success(JsArray(Seq(data)))
      case data: JsObject =>
        logger.error(s"[$hatAddress] Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case _ =>
        logger.error(s"[$hatAddress] Error parsing JSON object: ${rawData.toString}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }
  }
}

object FacebookProfileInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v2.10",
    "/me",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("fields" -> ("id,first_name,last_name,middle_name,name,link,age_range,birthday,email,languages," +
      "public_key,relationship_status,religion,significant_other,sports,friends")),
    Map(),
    Some(Map()))
}
