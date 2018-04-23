/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplug.dao

import javax.inject.{ Inject, Singleton }

import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.dal.Tables
import org.hatdex.libs.dal.SlickPostgresDriver
import org.hatdex.libs.dal.SlickPostgresDriver.api._
import org.hatdex.dataplug.apiInterfaces.models.DataPlugSharedNotable
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }

import scala.concurrent.Future

/**
 * Give access to the uploaded content object.
 */

@Singleton
class DataPlugSharedNotableDAOImpl @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)
  extends DataPlugSharedNotableDAO with HasDatabaseConfigProvider[SlickPostgresDriver] {

  import org.hatdex.dataplug.dal.ModelTranslation._

  implicit val ec = IoExecutionContext.ioThreadPool

  /**
   * Finds notable record by given ID.
   *
   * @param notableId Notable ID to be searched for.
   * @return Shared notable metadata record if found.
   */
  def find(notableId: String): Future[Option[DataPlugSharedNotable]] = {
    val q = Tables.SharedNotables.filter(notable => notable.id === notableId)

    db.run(q.result).map(_.headOption.map(fromDbModel))
  }

  /**
   * Saves metadata about the shared notable.
   *
   * @param notable Metadata about shared notable.
   * @return Saved version of the metadata about shared notable.
   */
  def save(notable: DataPlugSharedNotable): Future[DataPlugSharedNotable] = {
    // Essentially emulating the INSERT INTO ... ON CONFLICT UPDATE ... query
    val q = for {
      rowsAffected <- Tables.SharedNotables.filter(_.id === notable.id)
        .map(n => (n.posted, n.postedTime, n.deleted, n.deletedTime, n.providerId))
        .update(notable.posted, notable.postedTime.map(_.toLocalDateTime), notable.deleted, notable.deletedTime.map(_.toLocalDateTime), notable.providerId)
      result <- rowsAffected match {
        case 0 => Tables.SharedNotables += toDbModel(notable)
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(s"Expected 0 or 1 change, not $n for notable $notable"))
      }
    } yield result

    db.run(q).map(_ => notable)
  }

}
