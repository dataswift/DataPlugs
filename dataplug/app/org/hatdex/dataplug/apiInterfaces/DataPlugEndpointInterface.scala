package org.hatdex.dataplug.apiInterfaces

import java.net.URLEncoder

import akka.actor.ActorRef
import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticator
import org.hatdex.dataplug.apiInterfaces.models._
import org.hatdex.hat.api.models.ApiDataRecord
import org.joda.time.DateTime
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.JsValue
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugEndpointInterface extends HatDataOperations with RequestAuthenticator {
  val endpointName: String
  val refreshInterval: FiniteDuration
  protected val wsClient: WSClient
  protected val sourceName: String
  protected val quietTranslationErrors: Boolean
  protected val logger: Logger
  val defaultApiEndpoint: ApiEndpointCall

  /**
   * Fetch data from an API endpoint as per parametrised configuration, for a specific HAT client
   *
   * @param params API endpoint parameters generic (stateless) for the endpoint
   * @param hatAddress HAT Address (domain)
   * @param hatClient HAT client actor for specific HAT
   * @return Potentially updated set of parameters, e.g. with new timestamps
   */
  def fetch(fetchParams: ApiEndpointCall, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[DataPlugFetchStep] = {
    val authenticatedFetchParameters = authenticateRequest(fetchParams, hatAddress)

    authenticatedFetchParameters flatMap { requestParameters =>
      buildRequest(requestParameters)
    } flatMap { result =>
      result.status match {
        case OK =>
          processResults(result.json, hatAddress, hatClientActor) map { _ =>
            buildContinuation(result.json, fetchParams)
              .map(DataPlugFetchContinuation)
              .getOrElse(DataPlugFetchNextSync(buildNextSync(result.json, fetchParams)))
          }
        case UNAUTHORIZED =>
          logger.warn(s"Unauthorized request $fetchParams - ${result.status}: ${result.body}")
          Future.successful(DataPlugFetchNextSync(fetchParams))
        case NOT_FOUND =>
          logger.warn(s"Not found for request $fetchParams - ${result.status}: ${result.body}")
          Future.failed(new RuntimeException(s"Not found for request $fetchParams - ${result.status}: ${result.body}"))
        case _ =>
          logger.warn(s"Unsuccessful response from api endpoint $fetchParams - ${result.status}: ${result.body}")
          Future.successful(DataPlugFetchNextSync(fetchParams))
      }
    } recoverWith {
      case e =>
        logger.warn(s"Error when querying api endpoint $fetchParams - ${e.getMessage}")
        Future.failed(e)
    }
  }

  protected def buildRequest(params: ApiEndpointCall): Future[WSResponse] = {
    val path = params.pathParameters.foldLeft(params.path) { (path, parameter) =>
      path.replace(s"[${parameter._1}]", URLEncoder.encode(parameter._2, "UTF-8"))
    }
    val wsRequest = wsClient.url(params.url + path)
      .withQueryString(params.queryParameters.toList: _*)
      .withHeaders(params.headers.toList: _*)

    logger.debug(s"Sending WS request ${wsRequest.toString}")

    val response = params.method match {
      case ApiEndpointMethod.Get(_)        => wsRequest.get()
      case ApiEndpointMethod.Post(_, body) => wsRequest.post(body)
      case ApiEndpointMethod.Delete(_)     => wsRequest.delete()
      case ApiEndpointMethod.Put(_, body)  => wsRequest.put(body)
    }

    response
  }

  protected def processResults(content: JsValue, hatAddress: String, hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Unit] = {
    buildHatDataRecord(content, sourceName, endpointName).flatMap { hatData =>
      uploadHatData(hatData, hatClientActor)
    }
  }

  protected def uploadHatData(data: Seq[ApiDataRecord], hatClientActor: ActorRef)(implicit ec: ExecutionContext): Future[Unit] = {
    //hatClient ? HatClient.PostData(data)
    throw new RuntimeException("Not implemented")
  }

  /**
   * Build api endpoint parameters for a new data request. Most Plugs will need to overwrite
   *
   * @param params API endpoint parameters generic (stateless) for the endpoint
   * @return Potentially updated set of parameters, e.g. with new timestamps
   */
  def buildFetchParameters(params: Option[ApiEndpointCall])(implicit ec: ExecutionContext): Future[ApiEndpointCall] = {
    Future.successful(params getOrElse defaultApiEndpoint)
  }

  /**
   * Extract timestamp of data record to be stored in the HAT - HAT allows timestamp fields to
   * be set in the right format for easier handling later
   *
   * @param content JSON value of a successful response
   * @return DateTime-formatted timestamp - now by default
   */
  protected def extractRecordTimestamp(content: JsValue): DateTime = DateTime.now()

  /**
   * Build data fetch continuation API call parameters if the source has data paging.
   * Responsible for checking if there should be a further call for fetching data and configuring the endpoint
   *
   * @param content JSON value of a successful response
   * @param params pre-set API endpoint parameters to build from
   * @return Optional API endpoint configuration for continuation - None means no continuation
   */
  protected def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall]

  /**
   * Set up API call details for next synchronisation - set up any fields available at the last operation of the current
   * synchronisation round.
   *
   * @param content JSON value of a successful response
   * @param params pre-set API endpoint parameters to build from
   * @return Optional API endpoint configuration for continuation - None means no continuation
   */
  protected def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall

  /**
   * Asynchronously save endpoint status for a given hat address if data has been fetched successfully.
   *
   * @param content JSON value of a successful response
   * @param hatAddress address of the HAT for which to update plug status
   */
  //  protected def saveEndpointStatus(content: JsValue, hatAddress: String): Future[Unit]
}

