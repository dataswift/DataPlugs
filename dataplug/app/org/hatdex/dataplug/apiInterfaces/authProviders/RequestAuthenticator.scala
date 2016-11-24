package org.hatdex.dataplug.apiInterfaces.authProviders

import com.mohiva.play.silhouette.api.AuthInfo
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointCall

import scala.concurrent.{ ExecutionContext, Future }

trait RequestAuthenticator {
  type AuthInfoType <: AuthInfo
  def authenticateRequest(params: ApiEndpointCall, hatAddress: String)(implicit ec: ExecutionContext): Future[ApiEndpointCall]
}

