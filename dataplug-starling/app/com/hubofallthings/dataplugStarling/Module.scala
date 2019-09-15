/*
 * Copyright (C) Dataswift Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplugStarling

import akka.actor.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Provides }
import com.hubofallthings.dataplug.actors.DataPlugManagerActor
import com.hubofallthings.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry, DataPlugRegistry }
import com.hubofallthings.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import com.hubofallthings.dataplug.dal.SchemaMigrationImpl
import com.hubofallthings.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl }
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataPlugEndpointServiceImpl, StartupService, StartupServiceImpl }
import com.hubofallthings.dataplugStarling.apiInterfaces.authProviders.StarlingProvider
import com.hubofallthings.dataplugStarling.apiInterfaces.{ StarlingProfileCheck, StarlingProfileInterface, StarlingTransactionsInterface }
import com.mohiva.play.silhouette.api.Provider
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.providers._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.libs.dal.SchemaMigration
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

    //    bindActorFactory[InjectedHatClientActor, InjectedHatClientActor.Factory]
    bindActor[DataPlugManagerActor]("dataplug-manager")
  }

  /**
   * Provides the social provider registry.
   *
   * @param starlingProfileInterface The individual account holder api endpoint implementation, injected
   * @param starlingTransactionsInterface Starling transactions api endpoint implementation
   * @return The DataPlugRegistry.
   */
  @Provides
  def provideDataPlugCollection(
    starlingProfileInterface: StarlingProfileInterface,
    starlingTransactionsInterface: StarlingTransactionsInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(starlingProfileInterface, starlingTransactionsInterface))
  }

  @Provides
  def provideDataPlugEndpointChoiceCollection(
    starlingProvider: StarlingProvider,
    starlingProfileCheck: StarlingProfileCheck): DataPlugOptionsCollectorRegistry = {

    val variants: Seq[(Provider, DataPlugOptionsCollector)] = Seq((starlingProvider, starlingProfileCheck))
    DataPlugOptionsCollectorRegistry(variants)
  }

  /**
   * Provides the social provider registry.
   *
   * @param starlingProvider The Fitbit provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(
    starlingProvider: StarlingProvider): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      starlingProvider))
  }

  /**
   * Provides the Fitbit provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateHandler The OAuth2 state provider implementation.
   * @param configuration The Play configuration.
   * @return The Fitbit provider.
   */
  @Provides
  def provideSpotifyProvider(
    httpLayer: HTTPLayer,
    stateHandler: SocialStateHandler,
    configuration: Configuration): StarlingProvider = {
    new StarlingProvider(httpLayer, stateHandler, configuration.underlying.as[OAuth2Settings]("silhouette.starling"))
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }
}