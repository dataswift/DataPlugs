/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.models

import com.mohiva.play.silhouette.api.{ Identity, LoginInfo }

case class User(providerId: String, userId: String, linkedUsers: List[User]) extends Identity {
  def loginInfo: LoginInfo = LoginInfo(providerId, userId)
}
