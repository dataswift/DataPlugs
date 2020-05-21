/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplugFitbit

import com.hubofallthings.dataplugFitbit.apiInterfaces._
import com.hubofallthings.dataplugFitbit.apiInterfaces.authProviders.FitbitProvider
import akka.actor.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Provides }
import com.hubofallthings.dataplug.actors.DataPlugManagerActor
import com.hubofallthings.dataplug.apiInterfaces.authProviders.HatOAuth2Provider
import com.hubofallthings.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry, DataPlugRegistry }
import com.hubofallthings.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import com.hubofallthings.dataplug.dal.SchemaMigrationImpl
import com.hubofallthings.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl }
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataPlugEndpointServiceImpl, StartupService, StartupServiceImpl }
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
    bind[HatOAuth2Provider].to[FitbitProvider]

    //    bindActorFactory[InjectedHatClientActor, InjectedHatClientActor.Factory]
    bindActor[DataPlugManagerActor]("dataplug-manager")
  }

  /**
   * Provides the social provider registry.
   *
   * @return The DataPlugRegistry.
   */
  @Provides
  def provideDataPlugCollection(
    fitbitProfileInterface: FitbitProfileInterface,
    fitbitActivityInterface: FitbitActivityInterface,
    fitbitSleepInterface: FitbitSleepInterface,
    fitbitWeightInterface: FitbitWeightInterface,
    fitbitLifetimeStatsInterface: FitbitLifetimeStatsInterface,
    fitbitSleepGoalsInterface: FitbitSleepGoalsInterface,
    fitbitDailyActivityGoalsInterface: FitbitDailyActivityGoalsInterface,
    fitbitWeeklyAvtivityGoalsInterface: FitbitWeeklyAvtivityGoalsInterface,
    fitbitWeightGoalsInterface: FitbitWeightGoalsInterface,
    fitbitFatGoalsInterface: FitbitFatGoalsInterface,
    fitbitActivityDaySummaryInterface: FitbitActivityDaySummaryInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(
      fitbitProfileInterface,
      fitbitActivityInterface,
      fitbitSleepInterface,
      fitbitWeightInterface,
      fitbitLifetimeStatsInterface,
      fitbitSleepGoalsInterface,
      fitbitDailyActivityGoalsInterface,
      fitbitWeeklyAvtivityGoalsInterface,
      fitbitWeightGoalsInterface,
      fitbitFatGoalsInterface,
      fitbitActivityDaySummaryInterface))
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
      fitbitProvider))
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
  def provideFitbitProvider(
    httpLayer: HTTPLayer,
    stateHandler: SocialStateHandler,
    configuration: Configuration): FitbitProvider = {
    new FitbitProvider(httpLayer, stateHandler, configuration.underlying.as[OAuth2Settings]("silhouette.fitbit"))
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }

}