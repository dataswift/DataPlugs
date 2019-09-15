/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 3, 2019
 */

package com.hubofallthings.dataplugFacebook.models

import play.api.libs.json.{ Json, Reads }

case class FacebookUserLikes(
    id: String,
    about: Option[String],
    description: Option[String],
    description_html: Option[String])
//    fan_count: String,
//    has_added_app: Boolean,
//    has_whatsapp_number: Boolean,
//    link: String,
//    name: String,
//    phone: Option[String],
//    place_type: Option[String],
//    username: Option[String],
//    website: Option[String],
//    created_time: String)

object FacebookUserLikes {
  implicit val facebookUserLikesReads: Reads[FacebookUserLikes] = Json.reads[FacebookUserLikes]
}

