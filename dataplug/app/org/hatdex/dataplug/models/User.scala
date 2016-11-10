/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.models

import com.mohiva.play.silhouette.api.{ Identity, LoginInfo }

case class User(providerId: String, userId: String, linkedUsers: List[User]) extends Identity {
  def loginInfo: LoginInfo = LoginInfo(providerId, userId)
}
