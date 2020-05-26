/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.services

import akka.{ Done, NotUsed }
import akka.stream.scaladsl.Source
import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.dal.Tables
import com.hubofallthings.dataplug.models.HatAccessCredentials
import com.hubofallthings.dataplug.services.Errors.UserNotFoundException
import javax.inject.Inject
import org.hatdex.libs.dal.SlickPostgresDriver
import org.hatdex.libs.dal.SlickPostgresDriver.api._
import org.joda.time.DateTime
import play.api.Logger
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }

import scala.concurrent.Future

class HatTokenService @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[SlickPostgresDriver] {
  implicit val ec = IoExecutionContext.ioThreadPool

  protected val logger = Logger(this.getClass)

  def forUser(hat: String, issuedSince: Option[DateTime] = None): Future[Option[HatAccessCredentials]] = {
    val newerThan = issuedSince.getOrElse(DateTime.now().minusDays(3)).toLocalDateTime
    val q = Tables.HatToken.filter(h => h.hat === hat && h.dateCreated > newerThan)

    db.run(q.result).map(_.headOption.map(h => HatAccessCredentials(h.hat, h.accessToken)))
  }

  def allValid(issuedSince: Option[DateTime] = None): Future[Seq[HatAccessCredentials]] = {
    val newerThan = issuedSince.getOrElse(DateTime.now().minusDays(3)).toLocalDateTime
    val q = Tables.HatToken.filter(_.dateCreated > newerThan)

    db.run(q.result).map(_.map(t => HatAccessCredentials(t.hat, t.accessToken)))
  }

  def allValidStream(issuedSince: Option[DateTime] = None): Source[HatAccessCredentials, NotUsed] = {
    val newerThan = issuedSince.getOrElse(DateTime.now().minusDays(3)).toLocalDateTime
    val q = Tables.HatToken.filter(_.dateCreated > newerThan)

    Source.fromPublisher(db.stream(q.result.transactionally.withStatementParameters(fetchSize = 1000)))
      .map(t => HatAccessCredentials(t.hat, t.accessToken))
  }

  def save(hat: String, token: String, issued: DateTime): Future[Either[Done, Done]] = {
    val row = Tables.HatTokenRow(hat, token, issued.toLocalDateTime)
    val insert = Tables.HatToken += row
    val update = Tables.HatToken
      .filter(_.hat === hat)
      .map(t => (t.accessToken, t.dateCreated))
      .update((token, issued.toLocalDateTime))

    db.run(insert)
      .map(_ => Left(Done))
      .recoverWith({
        case _ => db.run(update).map(_ => Right(Done))
      })
  }

  def delete(hat: String): Future[Done] = {
    val q = Tables.HatToken.filter(_.hat === hat).delete
    db.run(q).map {
      case 0 => throw UserNotFoundException(s"Could not find user $hat")
      case 1 => Done
      case rows  =>
        logger.info(s"Deleting $rows HAT tokens for $hat")
        Done
    }
  }
}

object Errors {
  case class UserNotFoundException(message: String, cause: Throwable = None.orNull) extends Exception(message, cause)
}
