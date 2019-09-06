/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 7, 2019
 */

package com.hubofallthings.dataplugUber.models

import play.api.libs.json.{ Json, Reads, Writes }

case class UberProfile(
    first_name: String,
    last_name: String,
    email: String,
    picture: String,
    mobile_verified: Boolean,
    promo_code: String,
    uuid: String,
    rider_id: String)

object UberProfile {

  implicit val calendarsReads: Reads[UberProfile] = Json.reads[UberProfile]
  implicit val calendarsWrites: Writes[UberProfile] = Json.writes[UberProfile]
}
