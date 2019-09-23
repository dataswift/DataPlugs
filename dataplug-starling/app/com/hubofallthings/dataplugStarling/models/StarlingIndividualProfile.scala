/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugStarling.models

import play.api.libs.json.{ Json, Reads }

case class StarlingIndividualProfile(
    firstName: String,
    lastName: String,
    dateOfBirth: String,
    email: String,
    phone: String)

object StarlingIndividualProfile {
  implicit val starlingIndividualProfileReads: Reads[StarlingIndividualProfile] = Json.reads[StarlingIndividualProfile]
}
