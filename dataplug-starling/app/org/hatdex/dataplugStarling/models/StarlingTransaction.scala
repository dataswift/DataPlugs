package org.hatdex.dataplugStarling.models

import java.util.UUID

import play.api.libs.json.{ Json, Reads }

case class StarlingTransaction(
    feedItemUid: String,
    categoryUid: String,
    amount: StarlingAmount,
    sourceAmount: StarlingAmount,
    direction: String,
    updatedAt: String,
    transactionTime: String,
    settlementTime: String,
    sourceSubType: Option[String],
    source: String,
    status: String,
    counterPartyType: String,
    counterPartyUid: Option[String],
    counterPartyName: String,
    counterPartySubEntityUid: Option[String],
    counterPartySubEntityName: Option[String],
    counterPartySubEntityIdentifier: Option[String],
    counterPartySubEntitySubIdentifier: Option[String],
    reference: Option[String],
    country: Option[String],
    spendingCategory: Option[String],
    userNote: Option[String])

object StarlingTransaction {
  implicit val starlingTransactionReads: Reads[StarlingTransaction] = Json.reads[StarlingTransaction]
}
