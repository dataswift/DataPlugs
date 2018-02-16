/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.services

import java.sql.Connection
import javax.inject.Inject

import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.{ Contexts, LabelExpression, Liquibase }
import org.hatdex.dataplug.actors.IoExecutionContext
import play.api.db.{ Database, _ }
import play.api.{ Configuration, Logger }

import scala.collection.JavaConverters._
import scala.concurrent.{ Future, blocking }
import scala.util.Try

/**
 * Runs Liquibase based database schema and data migrations. This is the only place for all related
 * modules to run updates from.
 *
 * Liquibase finds its files on the classpath and applies them to DB. If migration fails
 * this class will throw an exception and by default your application should not continue to run.
 *
 * It does not matter which module runs this migration first.
 */
class SchemaMigrationImpl @Inject() (configuration: Configuration, @NamedDatabase("default") db: Database) extends SchemaMigration {

  import IoExecutionContext.ioThreadPool

  val logger = Logger(this.getClass)

  /**
   * Invoke this method to apply all DB migrations.
   */
  def run(changeLogFiles: Seq[String]): Future[Unit] = {
    logger.info(s"Running schema migrations: ${changeLogFiles.mkString(", ")}")
    changeLogFiles.foldLeft(Future(())) { (execution, evolution) => execution.flatMap { _ => updateDb(evolution) } }
  }

  private def updateDb(diffFilePath: String): Future[Unit] = {
    val eventuallyEsuccessful = Future {
      db.withTransaction { dbConnection =>
        logger.debug(s"Liquibase running evolutions $diffFilePath on db: [${dbConnection.getMetaData.getURL}]")
        val changesets = "structures,data"
        val liquibase = blocking {
          createLiquibase(dbConnection, diffFilePath)
        }
        blocking {
          listChangesets(liquibase, new Contexts(changesets))
          Try(liquibase.update(changesets))
            .recover {
              case e =>
                liquibase.forceReleaseLocks()
                logger.error(s"Error executing schema evolutions: ${e.getMessage}")
                throw e
            }
          liquibase.forceReleaseLocks()
        }
      }
    }

    eventuallyEsuccessful onFailure {
      case e =>
        logger.error(s"Error updating database: ${e.getMessage}")
    }

    eventuallyEsuccessful
  }

  private def listChangesets(liquibase: Liquibase, contexts: Contexts): Unit = {
    val changesetStatuses = liquibase.getChangeSetStatuses(contexts, new LabelExpression()).asScala

    logger.debug("Existing changesets:")
    changesetStatuses.foreach { cs =>
      if (cs.getWillRun) {
        logger.debug(s"${cs.getChangeSet.toString} will run")
      }
      else {
        logger.debug(s"${cs.getChangeSet.toString} will not run - previously executed on ${cs.getDateLastExecuted}")
      }
    }
  }

  private def createLiquibase(dbConnection: Connection, diffFilePath: String): Liquibase = {
    val classLoader = configuration.getClass.getClassLoader
    val resourceAccessor = new ClassLoaderResourceAccessor(classLoader)

    val database = DatabaseFactory.getInstance()
      .findCorrectDatabaseImplementation(new JdbcConnection(dbConnection))
    database.setDefaultSchemaName("hat")
    database.setLiquibaseSchemaName("public")
    new Liquibase(diffFilePath, resourceAccessor, database)
  }

}

