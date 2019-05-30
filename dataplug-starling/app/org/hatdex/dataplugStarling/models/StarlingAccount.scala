package org.hatdex.dataplugStarling.models

import play.api.libs.json.{ Json, Reads }

case class StarlingAccount(
    val accountUid: String,
    val defaultCategory: String,
    val currency: String,
    val createdAt: String)

object StarlingAccount {
  implicit val starlingIndividualProfileReads: Reads[StarlingAccount] = Json.reads[StarlingAccount]
}