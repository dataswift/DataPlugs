/*
 * Copyright (C) 2016-2020 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 04 2020
 */

package com.hubofallthings.dataplug.apiInterfaces.authProviders

import akka.Done
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider

import scala.concurrent.Future

trait HatOAuth2Provider extends OAuth2Provider {
  def disconnect(phata: String, userId: String): Future[Done] = Future.successful(Done)
}
