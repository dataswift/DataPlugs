/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.apiInterfaces.authProviders

import com.hubofallthings.dataplug.apiInterfaces.models.ApiEndpointCall
import com.mohiva.play.silhouette.api.AuthInfo

import scala.concurrent.{ ExecutionContext, Future }

trait RequestAuthenticator {
  type AuthInfoType <: AuthInfo
  def authenticateRequest(params: ApiEndpointCall, hatAddress: String, refreshToken: Boolean = true)(implicit ec: ExecutionContext): Future[ApiEndpointCall]
}

