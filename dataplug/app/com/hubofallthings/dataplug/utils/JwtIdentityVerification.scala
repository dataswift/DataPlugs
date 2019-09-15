/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.utils

import com.hubofallthings.dataplug.models.User
import com.nimbusds.jwt.SignedJWT

import scala.concurrent.Future

trait JwtIdentityVerification {
  def verifiedIdentity(identity: User, signedJWT: SignedJWT): Future[Option[User]]
}
