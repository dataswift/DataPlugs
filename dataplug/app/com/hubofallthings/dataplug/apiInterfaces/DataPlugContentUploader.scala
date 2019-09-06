/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplug.apiInterfaces

import com.hubofallthings.dataplug.apiInterfaces.authProviders.RequestAuthenticator
import com.hubofallthings.dataplug.apiInterfaces.models.DataPlugNotableShareRequest

import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugContentUploader extends RequestAuthenticator with DataPlugApiEndpointClient {
  def post(hatAddress: String, message: DataPlugNotableShareRequest)(implicit ec: ExecutionContext): Future[AnyRef]

  //def delete(hatAddress: String, notableId: String)(implicit ec: ExecutionContext): Future[AnyRef]
}
