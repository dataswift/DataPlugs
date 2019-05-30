package org.hatdex.dataplugStarling.models

import play.api.libs.json.{ Json, Reads }

case class StarlingAmount(
    currency: String,
    minorUnits: Int)

object StarlingAmount {
  implicit val starlingTransactionReads: Reads[StarlingAmount] = Json.reads[StarlingAmount]
}
