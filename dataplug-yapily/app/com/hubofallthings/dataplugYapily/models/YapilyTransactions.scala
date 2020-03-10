/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.models

import org.joda.time.DateTime
import play.api.libs.json.{ Json, Reads, Writes }

object TransactionsDateFormat {
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ"
  implicit val jodaDateReads = play.api.libs.json.JodaReads.jodaDateReads(dateFormat)
  implicit val jodaDateWrites = play.api.libs.json.JodaWrites.jodaDateWrites(dateFormat)
}

case class YapilyTransactions(
    id: Option[String],
    date: Option[DateTime],
    bookingDateTme: Option[DateTime],
    valueDateTime: Option[DateTime],
    status: Option[String],
    amount: Option[Double],
    currency: Option[String],
    reference: Option[String],
    description: Option[String],
    transactionAmount: Option[YapilyTransactionAmount])

object YapilyTransactions {
  import TransactionsDateFormat._
  implicit val transactionsReads: Reads[YapilyTransactions] = Json.reads[YapilyTransactions]
  implicit val transactionsdWrites: Writes[YapilyTransactions] = Json.writes[YapilyTransactions]
}

case class YapilyTransactionAmount(
    amount: Option[Double],
    currency: Option[String])

object YapilyTransactionAmount {
  implicit val transactionAmountReads: Reads[YapilyTransactionAmount] = Json.reads[YapilyTransactionAmount]
  implicit val transactionAmountWrites: Writes[YapilyTransactionAmount] = Json.writes[YapilyTransactionAmount]
}