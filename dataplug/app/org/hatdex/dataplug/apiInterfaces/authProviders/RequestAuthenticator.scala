/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.apiInterfaces.authProviders

import com.mohiva.play.silhouette.api.AuthInfo
import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointCall

import scala.concurrent.{ ExecutionContext, Future }

trait RequestAuthenticator {
  type AuthInfoType <: AuthInfo
  def authenticateRequest(params: ApiEndpointCall, hatAddress: String, refreshToken: Boolean = true)(implicit ec: ExecutionContext): Future[ApiEndpointCall]
}

