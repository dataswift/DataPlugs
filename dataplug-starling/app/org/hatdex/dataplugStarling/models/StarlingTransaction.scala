package org.hatdex.dataplugStarling.models

import java.util.UUID

import play.api.libs.json.{ Json, Reads }

case class StarlingTransaction(
    id: UUID,
    currency: String,
    amount: Double,
    direction: String,
    created: String,
    narrative: String,
    source: String,
    balance: Double)

object StarlingTransaction {
  implicit val starlingTransactionReads: Reads[StarlingTransaction] = Json.reads[StarlingTransaction]
}
