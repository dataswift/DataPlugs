/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplug.apiInterfaces

import org.hatdex.dataplug.apiInterfaces.authProviders.RequestAuthenticator

import scala.concurrent.{ ExecutionContext, Future }

trait DataPlugContentUploader extends RequestAuthenticator with DataPlugApiEndpointClient {
  def post(hatAddress: String, message: String)(implicit ec: ExecutionContext): Future[AnyRef]

  //def delete(hatAddress: String, notableId: String)(implicit ec: ExecutionContext): Future[AnyRef]
}
