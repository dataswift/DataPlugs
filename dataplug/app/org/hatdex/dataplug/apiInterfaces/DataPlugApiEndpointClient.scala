package org.hatdex.dataplug.apiInterfaces

import java.net.URLEncoder

import org.hatdex.dataplug.apiInterfaces.models._
import org.hatdex.dataplug.utils.Mailer
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugApiEndpointClient {
  val endpointName: String
  protected val wsClient: WSClient
  protected val sourceName: String
  protected val logger: Logger
  val defaultApiEndpoint: ApiEndpointCall
  val cacheApi: CacheApi
  val mailer: Mailer

  /**
   * Build api endpoint parameters for a new data request. Most Plugs will need to overwrite
   *
   * @param params API endpoint parameters generic (stateless) for the endpoint
   * @return Potentially updated set of parameters, e.g. with new timestamps
   */
  def buildFetchParameters(params: Option[ApiEndpointCall])(implicit ec: ExecutionContext): Future[ApiEndpointCall] = {
    Future.successful(params getOrElse defaultApiEndpoint)
  }

  protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val path = params.pathParameters.foldLeft(params.path) { (path, parameter) =>
      path.replace(s"[${parameter._1}]", URLEncoder.encode(parameter._2, "UTF-8"))
    }
    val wsRequest = wsClient.url(params.url + path)
      .withQueryString(params.queryParameters.toList: _*)
      .withHeaders(params.headers.toList: _*)

    logger.warn(s"Making request $wsRequest")

    val response = params.method match {
      case ApiEndpointMethod.Get(_)        => wsRequest.get()
      case ApiEndpointMethod.Post(_, body) => wsRequest.post(body)
      case ApiEndpointMethod.Delete(_)     => wsRequest.delete()
      case ApiEndpointMethod.Put(_, body)  => wsRequest.put(body)
    }

    response
  }
}
