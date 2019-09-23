/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 11, 2017
 */

package com.hubofallthings.dataplugMonzo.models

import play.api.libs.json.Json

case class AccountOwner(
    user_id: String,
    preferred_name: String,
    preferred_first_name: String)

object AccountOwner {
  implicit val accountOwnerFormat = Json.format[AccountOwner]
}

case class MonzoAccount(
    id: String,
    closed: Boolean,
    created: String,
    description: String,
    `type`: String,
    account_number: Option[String],
    sort_code: Option[String],
    owners: Seq[AccountOwner])

object MonzoAccount {
  implicit val accountFormat = Json.format[MonzoAccount]
}