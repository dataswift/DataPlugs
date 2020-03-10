/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.models

import play.api.libs.json.{ Json, Reads, Writes }

case class YapilyAccounts(
    id: String,
    `type`: Option[String],
    balance: Option[Double],
    currency: Option[String],
    usageType: Option[String],
    description: Option[String],
    accountType: Option[String],
    nickName: Option[String],
    accountNames: Option[Seq[YapilyAccountName]],
    accountIdentifications: Option[Seq[YapilyAccountIdentification]])

object YapilyAccounts {
  implicit val accountsReads: Reads[YapilyAccounts] = Json.reads[YapilyAccounts]
  implicit val accountsWrites: Writes[YapilyAccounts] = Json.writes[YapilyAccounts]
}

case class YapilyAccountName(name: Option[String])

object YapilyAccountName {
  implicit val accountNamesReads: Reads[YapilyAccountName] = Json.reads[YapilyAccountName]
  implicit val accountNamesWrites: Writes[YapilyAccountName] = Json.writes[YapilyAccountName]
}

case class YapilyAccountIdentification(
    `type`: Option[String],
    identification: Option[String])

object YapilyAccountIdentification {
  implicit val accountsIdReads: Reads[YapilyAccountIdentification] = Json.reads[YapilyAccountIdentification]
  implicit val accountsIdWrites: Writes[YapilyAccountIdentification] = Json.writes[YapilyAccountIdentification]
}
