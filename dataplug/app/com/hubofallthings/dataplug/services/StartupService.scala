/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.services

import akka.actor.{ ActorSystem, Scheduler }
import com.hubofallthings.dataplug.utils.FutureRetries
import javax.inject.{ Inject, Singleton }
import org.hatdex.libs.dal.SchemaMigration
import play.api.{ Configuration, Logger }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

trait StartupService

@Singleton
class StartupServiceImpl @Inject() (
    schemaMigration: SchemaMigration,
    configuration: Configuration,
    syncerActorManager: DataplugSyncerActorManager,
    actorSystem: ActorSystem,
    implicit val ec: ExecutionContext) extends StartupService {

  protected val logger = Logger(this.getClass)
  private val migrations: Seq[String] = configuration.get[Seq[String]]("slick.dbs.default.schemaMigrations")
  private implicit val scheduler: Scheduler = actorSystem.scheduler

  logger.info(s"Running database schema migrations on $migrations")
  val eventualMigrations: Future[Unit] = FutureRetries.retry(schemaMigration.run(migrations), List(5.seconds, 10.seconds, 20.seconds))

  eventualMigrations.onComplete {
    case Success(_) =>
      logger.info("Database migrations finished successfully")
      logger.info("Starting all active DataPlug variant choices")
      syncerActorManager.startAllActiveVariantChoices()
    case Failure(e) =>
      logger.error(s"Database migrations failed ${e.getMessage}")
  }
}
