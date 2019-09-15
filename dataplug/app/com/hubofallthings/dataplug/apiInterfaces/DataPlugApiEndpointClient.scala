/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.apiInterfaces

import java.net.URLEncoder

import com.hubofallthings.dataplug.actors.Errors.SourceApiCommunicationException
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import com.hubofallthings.dataplug.utils.Mailer
import play.api.Logger
import play.api.libs.ws.{ WSClient, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugApiEndpointClient {
  val endpoint: String
  protected val wsClient: WSClient
  protected val namespace: String
  protected val logger: Logger
  val defaultApiEndpoint: ApiEndpointCall
  val mailer: Mailer

  /**
   * Build api endpoint parameters for a new data request. Most Plugs will need to overwrite
   *
   * @param params API endpoint parameters generic (stateless) for the endpoint
   * @return Potentially updated set of parameters, e.g. with new timestamps
   */
  def buildFetchParameters(params: Option[ApiEndpointCall]): Future[ApiEndpointCall] = {
    Future.successful(params getOrElse defaultApiEndpoint)
  }

  protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val path = params.pathParameters.foldLeft(params.path) { (path, parameter) =>
      path.replace(s"[${parameter._1}]", URLEncoder.encode(parameter._2, "UTF-8"))
    }
    val wsRequest = wsClient.url(params.url + path)
      .withQueryStringParameters(params.queryParameters.toList: _*)
      .withHttpHeaders(params.headers.toList: _*)

    logger.debug(s"Making request $params")

    val response = params.method match {
      case ApiEndpointMethod.Get(_)        => wsRequest.get()
      case ApiEndpointMethod.Post(_, body) => wsRequest.post(body)
      case ApiEndpointMethod.Delete(_)     => wsRequest.delete()
      case ApiEndpointMethod.Put(_, body)  => wsRequest.put(body)
    }

    response recover {
      case e => throw SourceApiCommunicationException(s"Error executing request $params", e)
    }
  }
}
