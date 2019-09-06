/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.testkit

import com.hubofallthings.dataplug.models.User
import com.hubofallthings.dataplug.utils.JwtIdentityVerification
import com.nimbusds.jwt.SignedJWT
import play.api.Logger

import scala.concurrent.Future

class IdentityVerificationTestImpl extends JwtIdentityVerification {

  val logger = Logger("JwtPhataAuthentication")

  def verifiedIdentity(identity: User, signedJWT: SignedJWT): Future[Option[User]] = {
    Future.successful(Some(identity))
  }
}
