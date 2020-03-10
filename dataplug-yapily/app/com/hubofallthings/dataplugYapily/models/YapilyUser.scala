/*
 * Copyright (C) 2020 Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Marios Tsekis <marios.tsekis@dataswift.io>, 3 2020
 */

package com.hubofallthings.dataplugYapily.models

import java.util.UUID

import play.api.libs.json.{ Json, Reads, Writes }

case class UserModel(
    uuid: UUID,
    applicationUuid: UUID,
    applicationUserId: String,
    referenceId: String,
    institutionConsents: Seq[Institutions])

object UserModel {
  implicit val userReads: Reads[UserModel] = Json.reads[UserModel]
  implicit val userWrites: Writes[UserModel] = Json.writes[UserModel]
}

case class Institutions(institutionId: String)

object Institutions {
  implicit val institutionsReads: Reads[Institutions] = Json.reads[Institutions]
  implicit val institutionsWrites: Writes[Institutions] = Json.writes[Institutions]
}
