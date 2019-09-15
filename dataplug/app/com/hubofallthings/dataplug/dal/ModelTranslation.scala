/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.dal

import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointCall, ApiEndpointStatus, ApiEndpointVariant, DataPlugSharedNotable }
import com.hubofallthings.dataplug.models.User
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.{ OAuth1Info, OAuth2Info }
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json

trait ModelTranslation {
  import scala.language.implicitConversions
  import com.hubofallthings.dataplug.apiInterfaces.models.JsonProtocol._

  protected val logger: Logger

  implicit def fromDbModel(auth: Tables.UserOauth1InfoRow): OAuth1Info = {
    OAuth1Info(auth.token, auth.secret)
  }

  implicit def toDbModel(login: LoginInfo, auth: OAuth1Info): Tables.UserOauth1InfoRow = {
    Tables.UserOauth1InfoRow(login.providerID, login.providerKey, auth.token, auth.secret, DateTime.now().toLocalDateTime)
  }

  implicit def fromDbModel(auth: Tables.UserOauth2InfoRow): OAuth2Info = {
    OAuth2Info(auth.accessToken, auth.tokenType, auth.expiresIn, auth.refreshToken, auth.params.map(_.as[Map[String, String]]))
  }

  implicit def toDbModel(login: LoginInfo, auth: OAuth2Info): Tables.UserOauth2InfoRow = {
    Tables.UserOauth2InfoRow(login.providerID, login.providerKey, auth.accessToken, auth.tokenType, auth.expiresIn,
      auth.refreshToken, auth.params.map(Json.toJson(_)), DateTime.now().toLocalDateTime)
  }

  implicit def fromDbModel(endpoint: Tables.DataplugEndpointRow, user: Tables.DataplugUserRow): ApiEndpointVariant = {
    val plugEndpoint = ApiEndpoint(endpoint.name, endpoint.description, endpoint.details)

    ApiEndpointVariant(plugEndpoint, user.endpointVariant, user.endpointVariantDescription,
      user.endpointConfiguration.map(_.as[ApiEndpointCall]))
  }

  implicit def toDbModel(phata: String, plugName: String, variant: Option[String], configuration: Option[ApiEndpointCall], active: Boolean): Tables.DataplugUserRow = {
    Tables.DataplugUserRow(0, phata, plugName, configuration.map(c => Json.toJson(c)), variant, None, active, DateTime.now().toLocalDateTime)
  }

  implicit def fromDbModel(notable: Tables.SharedNotablesRow): DataPlugSharedNotable = {
    DataPlugSharedNotable(notable.id, notable.phata, notable.posted, notable.postedTime.map(_.toDateTime()),
      notable.providerId, notable.deleted, notable.deletedTime.map(_.toDateTime()))
  }

  implicit def toDbModel(notable: DataPlugSharedNotable): Tables.SharedNotablesRow = {
    Tables.SharedNotablesRow(notable.id, DateTime.now().toLocalDateTime, notable.phata, notable.posted,
      notable.postedTime.map(_.toLocalDateTime), notable.providerId, notable.deleted,
      notable.deletedTime.map(_.toLocalDateTime))
  }

  implicit def fromDbModel(user: Tables.UserUserRow, maybeLinkedUser: Option[Tables.UserLinkedUserRow]): User = {
    val linkedUser = maybeLinkedUser.map(lu => List(User(lu.providerId.get, lu.userId.get, List()))).getOrElse(List())
    User(user.providerId, user.userId, linkedUser)
  }

  implicit def toDbModel(phata: String, dataPlugEndpoint: String, configuration: Option[ApiEndpointCall], endpointVariant: Option[String] = None, created: org.joda.time.LocalDateTime, updated: org.joda.time.LocalDateTime, successful: Boolean, message: Option[String] = None): Tables.DataplugUserStatusRow = {
    val jsValue = Json.toJson(configuration)
    Tables.DataplugUserStatusRow(0, phata, dataPlugEndpoint, jsValue, endpointVariant, created, updated, successful, message)
  }

  implicit def fromDbModel(ldu: Tables.DataplugUserStatusRow, du: Tables.DataplugUserRow, de: Tables.DataplugEndpointRow): ApiEndpointStatus =
    ApiEndpointStatus(ldu.phata, fromDbModel(de, du), ldu.endpointConfiguration.as[ApiEndpointCall], ldu.updated.toDateTime(), ldu.successful, ldu.message)
}

object ModelTranslation extends ModelTranslation {
  protected val logger = Logger(this.getClass)
}
