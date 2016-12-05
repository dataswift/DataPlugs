package org.hatdex.dataplug.services

import javax.inject.Inject

import play.api.{ Configuration, Logger }

class SchemaMigrationLauncher @Inject() (configuration: Configuration, schemaMigration: SchemaMigration) {
  val logger = Logger("SchemaMigration")
  logger.info("Starting database schema migrations")
  configuration.getStringSeq("db.default.schemaMigrations").map { migrations =>
    logger.info(s"Running database schema migrations on $migrations")
    schemaMigration.run(migrations)
  }
}

