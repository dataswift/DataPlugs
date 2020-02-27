/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugInstagram.models

import play.api.libs.json._

case class InstagramProfile(
    id: String,
    username: String,
    account_type: String,
    media_count: Int)

object InstagramProfile {
  implicit val instagramProfileReads: Reads[InstagramProfile] = Json.reads[InstagramProfile]
}
