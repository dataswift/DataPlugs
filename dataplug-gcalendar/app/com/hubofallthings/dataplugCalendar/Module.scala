/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplugCalendar

import akka.actor.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Provides }
import com.hubofallthings.dataplug.actors.DataPlugManagerActor
import com.hubofallthings.dataplug.apiInterfaces.authProviders.HatOAuth2Provider
import com.hubofallthings.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry, DataPlugRegistry }
import com.hubofallthings.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import com.hubofallthings.dataplug.dal.SchemaMigrationImpl
import com.hubofallthings.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl }
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataPlugEndpointServiceImpl, StartupService, StartupServiceImpl }
import com.hubofallthings.dataplugCalendar.apiInterfaces.{ GoogleCalendarEventsInterface, GoogleCalendarList, GoogleCalendarsInterface }
import com.mohiva.play.silhouette.api.Provider
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.providers.{ OAuth2Settings, SocialProviderRegistry, SocialStateHandler }
import com.hubofallthings.dataplugCalendar.apiInterfaces.authProviders._
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
    bind[HatOAuth2Provider].to[GoogleProvider]

    bindActor[DataPlugManagerActor]("dataplug-manager")
  }

  /**
   * Provides the social provider registry.
   *
   * @param googleCalendarEventsInterface The google calendar api endpoint implementation, injected
   * @return The DataPlugRegistry.
   */
  @Provides
  def provideDataPlugCollection(
    googleCalendarEventsInterface: GoogleCalendarEventsInterface,
    googleCalendarsInterface: GoogleCalendarsInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(
      googleCalendarEventsInterface, googleCalendarsInterface))
  }

  @Provides
  def provideDataPlugEndpointChoiceCollection(
    googleProvider: GoogleProvider,
    googleCalendarList: GoogleCalendarList): DataPlugOptionsCollectorRegistry = {

    val variants: Seq[(Provider, DataPlugOptionsCollector)] = Seq((googleProvider, googleCalendarList))
    DataPlugOptionsCollectorRegistry(variants)
  }

  /**
   * Provides the social provider registry.
   *
   * @param googleProvider The Google provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(
    googleProvider: GoogleProvider): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      googleProvider))
  }

  /**
   * Provides the Google provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @param configuration The Play configuration.
   * @return The Google provider.
   */
  @Provides
  def provideGoogleProvider(
    httpLayer: HTTPLayer,
    stateProvider: SocialStateHandler,
    configuration: Configuration): GoogleProvider = {
    new GoogleProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.google"))
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }
}