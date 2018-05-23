/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplugMonzo

import akka.actor.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Provides }
import com.mohiva.play.silhouette.api.Provider
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.providers._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.dataplug.actors.DataPlugManagerActor
import org.hatdex.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry, DataPlugRegistry }
import org.hatdex.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import org.hatdex.dataplug.dal.SchemaMigrationImpl
import org.hatdex.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl }
import org.hatdex.dataplug.services._
import org.hatdex.libs.dal.SchemaMigration
import org.hatdex.dataplugMonzo.apiInterfaces.authProviders.MonzoProvider
import org.hatdex.dataplugMonzo.apiInterfaces.{ MonzoAccountList, MonzoTransactionsInterface }
import play.api.Configuration
import play.api.libs.concurrent.AkkaGuiceSupport

/**
 * The Guice module which wires all Silhouette dependencies.
 */
class Module extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  /**
   * Configures the module.
   */
  def configure() {
    // Automatic database schema migrations
    bind[SchemaMigration].to[SchemaMigrationImpl]
    bind[StartupService].to[StartupServiceImpl].asEagerSingleton()

    bind[DataPlugEndpointDAO].to[DataPlugEndpointDAOImpl]
    bind[DataPlugEndpointService].to[DataPlugEndpointServiceImpl]

    bind[DataPlugViewSet].to[DataPlugViewSetDefault]

    bindActor[DataPlugManagerActor]("dataplug-manager")
  }

  /**
   * Provides the social provider registry.
   *
   * @param googleCalendarEndpoint The google calendar api endpoint implementation, injected
   * @return The DataPlugRegistry.
   */
  @Provides
  def provideDataPlugCollection(
    monzoTransactionsInterface: MonzoTransactionsInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(
      monzoTransactionsInterface))
  }

  @Provides
  def provideDataPlugEndpointChoiceCollection(
    monzoProvider: MonzoProvider,
    monzoAccountList: MonzoAccountList): DataPlugOptionsCollectorRegistry = {

    val variants: Seq[(Provider, DataPlugOptionsCollector)] = Seq((monzoProvider, monzoAccountList))
    DataPlugOptionsCollectorRegistry(variants)
  }

  /**
   * Provides the social provider registry.
   *
   * @param monzoProvider The Monzo provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(
    monzoProvider: MonzoProvider): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      monzoProvider))
  }

  /**
   * Provides the Monzo provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateHandler The OAuth2 state provider implementation.
   * @param configuration The Play configuration.
   * @return The Monzo provider.
   */
  @Provides
  def provideMonzoProvider(
    httpLayer: HTTPLayer,
    stateHandler: SocialStateHandler,
    configuration: Configuration): MonzoProvider = {
    new MonzoProvider(httpLayer, stateHandler, configuration.underlying.as[OAuth2Settings]("silhouette.monzo"))
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }

}