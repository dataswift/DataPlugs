/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.modules

import akka.actor.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Provides }
import com.mohiva.play.silhouette.impl.providers._
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.dataplug.actors.{ DataPlugManagerActor }
import org.hatdex.dataplug.apiInterfaces.DataPlugRegistry
import org.hatdex.dataplug.dao._
import org.hatdex.dataplug.services._
import play.api.libs.concurrent.AkkaGuiceSupport

/**
 * The Guice module which wires all Silhouette dependencies.
 */
class DataplugModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  /**
   * Configures the module.
   */
  def configure() {
    bind[DataPlugEndpointDAO].to[DataPlugEndpointDAOImpl]
    bind[DataPlugSharedNotableDAO].to[DataPlugSharedNotableDAOImpl]
    bind[DataPlugEndpointService].to[DataPlugEndpointServiceImpl]
    bind[DataPlugNotablesService].to[DataPlugNotablesServiceImpl]

    //    bindActorFactory[InjectedHatClientActor, InjectedHatClientActor.Factory]
    bindActor[DataPlugManagerActor]("dataplug-manager")
  }

  /**
   * Provides the social provider registry.
   *
   * @return The DataPlugRegistry.
   */
  @Provides
  def provideDataPlugCollection(): DataPlugRegistry = {

    DataPlugRegistry(Seq())
  }

  /**
   * Provides the social provider registry.
   *
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(): SocialProviderRegistry = {

    SocialProviderRegistry(Seq())
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }
}