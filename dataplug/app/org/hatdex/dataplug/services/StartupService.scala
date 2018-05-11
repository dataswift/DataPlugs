package org.hatdex.dataplug.services

import javax.inject.{ Inject, Singleton }
import org.hatdex.libs.dal.SchemaMigration
import play.api.{ Configuration, Logger }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait StartupService

@Singleton
class StartupServiceImpl @Inject() (
    schemaMigration: SchemaMigration,
    configuration: Configuration,
    syncerActorManager: DataplugSyncerActorManager,
    implicit val ec: ExecutionContext) extends StartupService {

  protected val logger = Logger(this.getClass)
  private val migrations: Seq[String] = configuration.get[Seq[String]]("slick.dbs.default.schemaMigrations")

  logger.info(s"Running database schema migrations on $migrations")
  val eventualMigrations: Future[Unit] = schemaMigration.run(migrations)

  eventualMigrations.onComplete {
    case Success(_) => logger.info("Database migrations finished successfully")
    case Failure(e) => logger.error(s"Database migrations failed ${e.getMessage}")
  }

  logger.info("Starting all active DataPlug variant choices")
  syncerActorManager.startAllActiveVariantChoices()
}
