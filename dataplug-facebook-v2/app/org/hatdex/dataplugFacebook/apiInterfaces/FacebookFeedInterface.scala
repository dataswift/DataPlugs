package org.hatdex.dataplugFacebook.apiInterfaces

import akka.actor.{ ActorRef, Scheduler }
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider
import org.hatdex.commonPlay.utils.FutureTransformations
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFacebook.models.FacebookPost
import org.joda.time.DateTime
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FacebookFeedInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val cacheApi: CacheApi,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FacebookProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "facebook"
  val endpoint: String = "feed"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = FacebookFeedInterface.defaultApiEndpoint

  val refreshInterval = 1.hour

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val maybeNextPage = (content \ "paging" \ "next").asOpt[String]
    val maybeSinceParam = params.pathParameters.get("since")

    maybeNextPage.map { nextPage =>
      logger.debug(s"Found next page link (continuing sync): $nextPage")

      val nextPageUri = Uri(nextPage)
      val updatedQueryParams = params.queryParameters ++ nextPageUri.query().toMap

      logger.debug(s"Updated query parameters: $updatedQueryParams")

      if (maybeSinceParam.isDefined) {
        logger.debug("\"Since\" parameter already set, updating query params")
        params.copy(queryParameters = updatedQueryParams)
      }
      else {
        (content \ "paging" \ "previous").asOpt[String].flatMap { previousPage =>
          val previousPageUri = Uri(previousPage)
          previousPageUri.query().get("since").map { sinceParam =>
            val updatedPathParams = params.pathParameters + ("since" -> sinceParam)

            logger.debug(s"Updating query params and setting 'since': $sinceParam")
            params.copy(pathParameters = updatedPathParams, queryParameters = updatedQueryParams)
          }
        }.getOrElse {
          logger.warn("Unexpected API behaviour: 'since' not set and it was not possible to extract it from response body")
          params.copy(queryParameters = updatedQueryParams)
        }
      }
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    val maybeSinceParam = params.pathParameters.get("since")
    val updatedQueryParams = params.queryParameters - "__paging_token" - "until" - "access_token"

    logger.debug(s"Updated query parameters: $updatedQueryParams")

    maybeSinceParam.map { sinceParam =>
      logger.debug(s"Building next sync parameters $updatedQueryParams with 'since': $sinceParam")
      params.copy(pathParameters = params.pathParameters - "since", queryParameters = updatedQueryParams + ("since" -> sinceParam))
    }.getOrElse {
      val maybePreviousPage = (content \ "paging" \ "previous").asOpt[String]

      logger.debug("'Since' parameter not found (likely no continuation runs), setting one now")
      maybePreviousPage.flatMap { previousPage =>
        Uri(previousPage).query().get("since").map { newSinceParam =>
          params.copy(queryParameters = params.queryParameters + ("since" -> newSinceParam))
        }
      }.getOrElse {
        logger.warn("Could not extract previous page 'since' parameter so the new value is not set. Was the feed list empty?")
        params
      }
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
    (rawData \ "data").toOption.map {
      case data: JsArray if data.validate[List[FacebookPost]].isSuccess =>
        logger.debug(s"Validated JSON object:\n${data.toString}")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"Error parsing JSON object: ${data.validate[List[FacebookPost]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object, necessary property not found: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object, necessary property not found."))
    }
  }

}

object FacebookFeedInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v2.10",
    "/me/feed",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "500", "format" -> "json", "fields" -> ("id,admin_creator,application,call_to_action,caption,created_time,description," +
      "feed_targeting,from,icon,is_hidden,is_published,link,message,message_tags,name,object_id,picture,place," +
      "privacy,properties,shares,source,status_type,story,targeting,to,type,updated_time,with_tags,full_picture")),
    Map())
}
