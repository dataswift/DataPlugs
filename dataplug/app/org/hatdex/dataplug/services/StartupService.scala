package org.hatdex.dataplug.services

import javax.inject.{ Inject, Singleton }

import play.api.Logger

import scala.concurrent.ExecutionContext

trait StartupService

@Singleton
class StartupServiceImpl @Inject() (
    syncerActorManager: DataplugSyncerActorManager,
    implicit val ec: ExecutionContext) extends StartupService {

  val logger = Logger(this.getClass)
  logger.info("Starting all active DataPlug variant choices")
  syncerActorManager.startAllActiveVariantChoices()
}
