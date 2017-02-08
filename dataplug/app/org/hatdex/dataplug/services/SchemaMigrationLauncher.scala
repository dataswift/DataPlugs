/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

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

