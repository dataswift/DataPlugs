package org.hatdex.dataplug.dal

import javax.inject.Inject

import org.hatdex.libs.dal.BaseSchemaMigrationImpl
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }
import play.api.{ Configuration, Logger }
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

/**
 * Runs Liquibase based database schema and data migrations. This is the only place for all related
 * modules to run updates from.
 *
 * Liquibase finds its files on the classpath and applies them to DB. If migration fails
 * this class will throw an exception and by default your application should not continue to run.
 *
 * It does not matter which module runs this migration first.
 */
class SchemaMigrationImpl @Inject() (
    config: Configuration,
    val dbConfigProvider: DatabaseConfigProvider,
    implicit val ec: ExecutionContext)
  extends BaseSchemaMigrationImpl with HasDatabaseConfigProvider[JdbcProfile] {

  protected val configuration = config.underlying
  protected val logger = Logger(this.getClass).logger
}
