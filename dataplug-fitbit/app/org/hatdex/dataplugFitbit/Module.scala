/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplugFitbit

import com.google.inject.{ AbstractModule, Provides }
import com.mohiva.play.silhouette.api.Provider
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.providers._
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.dataplug.actors.{ DataPlugManagerActor, InjectedHatClientActor }
import org.hatdex.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry, DataPlugRegistry }
import org.hatdex.dataplug.controllers.DataPlugViewSet
import org.hatdex.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl }
import org.hatdex.dataplug.services._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.hatdex.dataplugFitbit.apiInterfaces._
import org.hatdex.dataplugFitbit.controllers.DataPlugViewSetFitbit
import org.hatdex.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
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
    bind[SchemaMigrationLauncher].asEagerSingleton()

    bind[DataPlugEndpointDAO].to[DataPlugEndpointDAOImpl]
    bind[DataPlugEndpointService].to[DataPlugEndpointServiceImpl]

    bind[DataPlugViewSet].to[DataPlugViewSetFitbit]

    bindActorFactory[InjectedHatClientActor, InjectedHatClientActor.Factory]
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
    fitbitProfileInterface: FitbitProfileInterface,
    fitbitActivityInterface: FitbitActivityInterface,
    fitbitSleepInterface: FitbitSleepInterface,
    fitbitFatInterface: FitbitFatInterface,
    fitbitWeightInterface: FitbitWeightInterface,
    fitbitLifetimeStatsInterface: FitbitLifetimeStatsInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(
      fitbitActivityInterface, fitbitSleepInterface, fitbitFatInterface, fitbitWeightInterface,
      fitbitProfileInterface, fitbitLifetimeStatsInterface
    ))
  }

  @Provides
  def provideDataPlugEndpointChoiceCollection(
    fitbitProvider: FitbitProvider,
    fitbitProfileCheck: FitbitProfileCheck): DataPlugOptionsCollectorRegistry = {

    val variants: Seq[(Provider, DataPlugOptionsCollector)] = Seq((fitbitProvider, fitbitProfileCheck))
    DataPlugOptionsCollectorRegistry(variants)
  }

  /**
   * Provides the social provider registry.
   *
   * @param fitbitProvider The Fitbit provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(
    fitbitProvider: FitbitProvider): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      fitbitProvider
    ))
  }

  /**
   * Provides the Fitbit provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @param configuration The Play configuration.
   * @return The Fitbit provider.
   */
  @Provides
  def provideFitbitProvider(
    httpLayer: HTTPLayer,
    stateProvider: OAuth2StateProvider,
    configuration: Configuration): FitbitProvider = {
    new FitbitProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.fitbit"))
  }

}