package org.hatdex.dataplugStarling.models

import java.util.UUID

import play.api.libs.json.{ Json, Reads }

case class StarlingTransaction(
    feedItemUid: UUID,
    categoryUid: UUID,
    amount: StarlingCurrencyAndAmount,
    sourceAmount: StarlingCurrencyAndAmount,
    direction: String,
    transactionTime: String,
    source: String,
    sourceSubType: String,
    status: String,
    counterPartyType: String,
    counterPartyUid: UUID,
    counterPartySubEntityUid: UUID,
    reference: String,
    country: String,
    spendingCategory: String)

case class StarlingCurrencyAndAmount(
    currency: String,
    minorUnits: Long)

object StarlingTransaction {
  implicit val starlingTransactionReads: Reads[StarlingTransaction] = Json.reads[StarlingTransaction]
}

object StarlingCurrencyAndAmount {
  implicit val starlingCurrencyAndAmountReads: Reads[StarlingCurrencyAndAmount] = Json.reads[StarlingCurrencyAndAmount]
}
