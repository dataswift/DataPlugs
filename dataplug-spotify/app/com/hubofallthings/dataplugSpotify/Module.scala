/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplugSpotify

import akka.actor.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Provides }
import com.hubofallthings.dataplug.actors.DataPlugManagerActor
import com.hubofallthings.dataplug.apiInterfaces.authProviders.HatOAuth2Provider
import com.hubofallthings.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry, DataPlugRegistry }
import com.hubofallthings.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import com.hubofallthings.dataplug.dal.SchemaMigrationImpl
import com.hubofallthings.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl }
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataPlugEndpointServiceImpl, StartupService, StartupServiceImpl }
import com.hubofallthings.dataplugSpotify.apiInterfaces.authProviders.SpotifyProvider
import com.hubofallthings.dataplugSpotify.apiInterfaces.{ SpotifyProfileCheck, SpotifyProfileInterface, SpotifyRecentlyPlayedInterface, SpotifyUserPlaylistTracksInterface, SpotifyUserPlaylistsInterface }
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
    bind[HatOAuth2Provider].to[SpotifyProvider]

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
    spotifyProfileInterface: SpotifyProfileInterface,
    spotifyUserPlaylistInterface: SpotifyUserPlaylistsInterface,
    spotifyUserPlaylistTracksInterface: SpotifyUserPlaylistTracksInterface,
    spotifyRecentlyPlayedInterface: SpotifyRecentlyPlayedInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(spotifyProfileInterface, spotifyUserPlaylistInterface, spotifyUserPlaylistTracksInterface, spotifyRecentlyPlayedInterface))
  }

  @Provides
  def provideDataPlugEndpointChoiceCollection(
    spotifyProvider: SpotifyProvider,
    spotifyProfileCheck: SpotifyProfileCheck): DataPlugOptionsCollectorRegistry = {

    val variants: Seq[(Provider, DataPlugOptionsCollector)] = Seq((spotifyProvider, spotifyProfileCheck))
    DataPlugOptionsCollectorRegistry(variants)
  }

  /**
   * Provides the social provider registry.
   *
   * @param spotifyProvider The Spotify provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(
    spotifyProvider: SpotifyProvider): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      spotifyProvider))
  }

  /**
   * Provides the Spotify provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateHandler The OAuth2 state provider implementation.
   * @param configuration The Play configuration.
   * @return The Spotify provider.
   */
  @Provides
  def provideSpotifyProvider(
    httpLayer: HTTPLayer,
    stateHandler: SocialStateHandler,
    configuration: Configuration): SpotifyProvider = {
    new SpotifyProvider(httpLayer, stateHandler, configuration.underlying.as[OAuth2Settings]("silhouette.spotify"))
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }

}