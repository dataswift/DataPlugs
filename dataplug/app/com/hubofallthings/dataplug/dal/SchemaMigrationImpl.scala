/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.dal

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

  override protected val changeContexts = "structures,data"
  override protected val defaultSchemaName = "public"
  override protected val liquibaseSchemaName = "public"

  protected val configuration = config.underlying
  protected val logger = Logger(this.getClass).logger
}
