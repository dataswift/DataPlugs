/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.apiInterfaces

import java.net.URLEncoder

import com.google.inject.Inject
import com.hubofallthings.dataplug.actors.Errors.SourceApiCommunicationException
import com.hubofallthings.dataplug.apiInterfaces.DataPlugEndpointInterface
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import com.hubofallthings.dataplugYapily.apiInterfaces.authProviders.YapilyProvider
import play.api.libs.ws.{ WSAuthScheme, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }

abstract class YapilyEndpointInterface @Inject() (provider: YapilyProvider) extends DataPlugEndpointInterface {

  override protected def buildRequest(params: ApiEndpointCall)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val token: String = params.headers.getOrElse("Authorization", "").split(' ').last
    val path = params.pathParameters.foldLeft(params.path) { (path, parameter) =>
      path.replace(s"[${parameter._1}]", URLEncoder.encode(parameter._2, "UTF-8"))
    }
    val wsRequest = wsClient.url(params.url + path)
      .withAuth(provider.settings.clientID, provider.settings.clientSecret, WSAuthScheme.BASIC)
      .withQueryStringParameters(params.queryParameters.toList: _*)
      .addHttpHeaders("Consent" -> token)

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
