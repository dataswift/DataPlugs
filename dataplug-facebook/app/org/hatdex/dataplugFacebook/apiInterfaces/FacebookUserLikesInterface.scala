package org.hatdex.dataplugFacebook.apiInterfaces

import akka.actor.Scheduler
import akka.http.scaladsl.model.Uri
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugFacebook.models.{ FacebookPost, FacebookUserLikes }
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject, JsValue }
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class FacebookUserLikesInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: FacebookProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val defaultApiEndpoint = FacebookUserLikesInterface.defaultApiEndpoint

  val namespace: String = "facebook"
  val endpoint: String = "likes/pages"
  protected val logger: Logger = Logger(this.getClass)

  val refreshInterval = 1.hour // No idea if this is ideal, might do with longer?

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val maybeNextPage = (content \ "paging" \ "cursors" \ "next").asOpt[String]
    val maybeAfterParam = params.pathParameters.get("after")

    maybeNextPage.map { nextPage =>
      logger.debug(s"Found next page link (continuing sync): $nextPage")

      val nextPageUri = Uri(nextPage)
      val updatedQueryParams = params.queryParameters ++ nextPageUri.query().toMap

      logger.debug(s"Updated query parameters: $updatedQueryParams")

      if (maybeAfterParam.isDefined) {
        logger.debug("\"After\" parameter already set, updating query params")
        params.copy(queryParameters = updatedQueryParams)
      }
      else {
        (content \ "paging" \ "cursors" \ "previous").asOpt[String].flatMap { previousPage =>
          val previousPageUri = Uri(previousPage)
          previousPageUri.query().get("before").map { beforeParam =>
            val updatedPathParams = params.pathParameters + ("before" -> beforeParam)

            logger.debug(s"Updating query params and setting 'before': $beforeParam")
            params.copy(pathParameters = updatedPathParams, queryParameters = updatedQueryParams)
          }
        }.getOrElse {
          logger.warn("Unexpected API behaviour: 'before' not set and it was not possible to extract it from response body")
          params.copy(queryParameters = updatedQueryParams)
        }
      }
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    val maybeAfterParam = params.pathParameters.get("after")
    val updatedQueryParams = params.queryParameters - "after" - "access_token"

    logger.debug(s"Updated query parameters: $updatedQueryParams")

    maybeAfterParam.map { afterParam =>
      logger.debug(s"Building next sync parameters $updatedQueryParams with 'after': $afterParam")
      params.copy(pathParameters = params.pathParameters - "after", queryParameters = updatedQueryParams + ("after" -> afterParam))
    }.getOrElse {
      val maybePreviousPage = (content \ "paging" \ "cursors" \ "previous").asOpt[String]

      logger.debug("'before' parameter not found (likely no continuation runs), setting one now")
      maybePreviousPage.flatMap { previousPage =>
        Uri(previousPage).query().get("before").map { newBeforeParam =>
          params.copy(queryParameters = params.queryParameters + ("before" -> newBeforeParam))
        }
      }.getOrElse {
        logger.warn("Could not extract previous page 'before' parameter so the new value is not set. Was the feed list empty?")
        params
      }
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "data").toOption.map {
      case data: JsArray if data.validate[List[FacebookUserLikes]].isSuccess =>
        logger.info(s"Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsArray =>
        logger.warn(s"Could not validate full item list. Parsing ${data.value.length} data items one by one.")
        Success(JsArray(data.value.filter(_.validate[FacebookUserLikes].isSuccess)))
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

object FacebookUserLikesInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://graph.facebook.com/v2.10",
    "/me/likes",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("limit" -> "500", "fields" -> ("id,about,created_time,app_links,awards,can_checkin,can_post,category,category_list,checkins," +
      "description,description_html,display_subtext,emails,fan_count,has_added_app,has_whatsapp_number,link," +
      "location,name, overall_star_rating,phone,place_type,rating_count,username, verification_status, website, whatsapp_number")),
    Map(),
    Some(Map()))
}
