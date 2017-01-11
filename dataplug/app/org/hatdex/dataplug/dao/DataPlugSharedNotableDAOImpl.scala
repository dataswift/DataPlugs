/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplug.dao

import javax.inject.{ Inject, Singleton }

import anorm.{ Macro, ResultSetParser, RowParser, SQL }
import anorm.JodaParameterMetaData._
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.DataPlugSharedNotable
import play.api.db.{ Database, NamedDatabase }

import scala.concurrent.{ Future, blocking }

/**
 * Give access to the uploaded content object.
 */

@Singleton
class DataPlugSharedNotableDAOImpl @Inject() (@NamedDatabase("default") db: Database) extends DataPlugSharedNotableDAO {
  implicit val ec = IoExecutionContext.ioThreadPool

  private def sharedNotablesParser: RowParser[DataPlugSharedNotable] =
    Macro.parser[DataPlugSharedNotable](
      "shared_notables.id",
      "shared_notables.phata",
      "shared_notables.posted",
      "shared_notables.posted_time",
      "shared_notables.provider_id",
      "shared_notables.deleted",
      "shared_notables.deleted_time")

  private def singleSharedNotableInfoParser: ResultSetParser[DataPlugSharedNotable] =
    sharedNotablesParser.single

  /**
   * Finds notable record by given ID.
   *
   * @param notableId Notable ID to be searched for.
   * @return Shared notable metadata record if found.
   */
  def find(notableId: String): Future[Option[DataPlugSharedNotable]] =
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          val foundNotables = SQL(
            """
              | SELECT * FROM shared_notables
              | WHERE
              |   shared_notables.id = {notableId}
            """.stripMargin)
            .on('notableId -> notableId)
            .as(sharedNotablesParser.*)

          foundNotables.headOption
        }
      }
    }

  /**
   * Saves metadata about the shared notable.
   *
   * @param notable Metadata about shared notable.
   * @return Saved version of the metadata about shared notable.
   */
  def save(notable: DataPlugSharedNotable) = {
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | INSERT INTO shared_notables (id, phata, posted, posted_time, provider_id, deleted, deleted_time)
              | VALUES ({notableId}, {phata}, {posted}, {postedTime}, {providerId}, {deleted}, {deletedTime})
              | ON CONFLICT (id) DO UPDATE SET
              |   posted = {posted},
              |   posted_time = {postedTime},
              |   deleted = {deleted},
              |   deleted_time = {deletedTime},
              |   provider_id = {providerId}
            """.stripMargin)
            .on(
              'notableId -> notable.id,
              'phata -> notable.phata,
              'posted -> notable.posted,
              'postedTime -> notable.postedTime,
              'deleted -> notable.deleted,
              'deletedTime -> notable.deletedTime,
              'providerId -> notable.providerId
            )
            .executeInsert(singleSharedNotableInfoParser)
        }
      }
    }
  }

}
