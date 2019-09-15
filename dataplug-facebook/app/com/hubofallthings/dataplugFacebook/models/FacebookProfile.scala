/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 5, 2017
 */

package com.hubofallthings.dataplugFacebook.models

import play.api.libs.json._

case class FacebookProfile(
    id: String,
    birthday: Option[String],
    email: Option[String],
    first_name: String,
    last_name: String,
    gender: Option[String], //need extra permissions
    name: String,
    age_range: Option[String],
    link: Option[String])

case class FacebookBasicUser(
    name: String,
    id: String)

case class FacebookHometown(
    id: String,
    name: String)

case class FacebookSignificantOther(
    id: String,
    name: String)

object FacebookProfile {
  implicit val facebookProfileReads: Reads[FacebookProfile] = Json.reads[FacebookProfile]
}
