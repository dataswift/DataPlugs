package org.hatdex.dataplugMonzo.models

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