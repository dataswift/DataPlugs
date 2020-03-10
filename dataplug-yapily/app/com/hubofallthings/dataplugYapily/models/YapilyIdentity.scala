/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.models

import play.api.libs.json.{ Json, Reads, Writes }

case class YapilyIdentity(
    id: String,
    firstName: Option[String],
    lastName: Option[String],
    fullName: Option[String],
    gender: Option[String],
    birthdate: Option[String],
    email: Option[String],
    phone: Option[String],
    addresses: Option[YapilyAddress])

object YapilyIdentity {
  implicit val identityReads: Reads[YapilyIdentity] = Json.reads[YapilyIdentity]
  implicit val identityWrites: Writes[YapilyIdentity] = Json.writes[YapilyIdentity]
}

case class YapilyAddress(
    addressLines: Option[Seq[String]],
    city: Option[String],
    country: Option[String],
    postalCode: Option[String])

object YapilyAddress {
  implicit val addressReads: Reads[YapilyAddress] = Json.reads[YapilyAddress]
  implicit val addressWrites: Writes[YapilyAddress] = Json.writes[YapilyAddress]
}
