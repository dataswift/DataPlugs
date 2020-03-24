/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugInstagram.models

import play.api.libs.json.{ Json, OFormat }

case class InstagramMedia(
    media_type: String,
    caption: Option[String],
    media_url: String,
    permalink: String,
    username: String,
    timestamp: String,
    id: String)

object InstagramMedia {
  implicit val instagramMediaReads: OFormat[InstagramMedia] = Json.format[InstagramMedia]
}
