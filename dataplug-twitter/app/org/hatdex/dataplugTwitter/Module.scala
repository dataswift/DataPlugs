/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplugTwitter

import akka.actor.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Provides }
import com.mohiva.play.silhouette.api.Provider
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider
import com.mohiva.play.silhouette.impl.providers.oauth1.services.PlayOAuth1Service
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.dataplug.actors.DataPlugManagerActor
import org.hatdex.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry, DataPlugRegistry }
import org.hatdex.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import org.hatdex.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl, DataPlugSharedNotableDAO, DataPlugSharedNotableDAOImpl }
import org.hatdex.dataplug.services._
import org.hatdex.libs.dal.SchemaMigration
import org.hatdex.dataplug.dal.SchemaMigrationImpl
import org.hatdex.dataplugTwitter.apiInterfaces._
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
    bind[DataPlugSharedNotableDAO].to[DataPlugSharedNotableDAOImpl]
    bind[DataPlugEndpointService].to[DataPlugEndpointServiceImpl]
    bind[DataPlugNotablesService].to[DataPlugNotablesServiceImpl]

    bind[DataPlugViewSet].to[DataPlugViewSetDefault]

    bindActor[DataPlugManagerActor]("dataplug-manager")
  }

  /**
   * Provides the social provider registry.
   *
   * @param twitterInterface The google calendar api endpoint implementation, injected
   * @return The DataPlugRegistry.
   */
  @Provides
  def provideDataPlugCollection(twitterInterface: TwitterTweetInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(twitterInterface))
  }

  @Provides
  def provideDataPlugEndpointChoiceCollection(
    twitterProvider: TwitterProvider,
    twitterTweetsCheck: TwitterTweetsCheck): DataPlugOptionsCollectorRegistry = {

    val variants: Seq[(Provider, DataPlugOptionsCollector)] = Seq(
      (twitterProvider, twitterTweetsCheck))
    DataPlugOptionsCollectorRegistry(variants)
  }

  /**
   * Provides the social provider registry.
   *
   * @param twitterProvider The Twitter provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(twitterProvider: TwitterProvider): SocialProviderRegistry = {
    SocialProviderRegistry(Seq(
      twitterProvider))
  }

  /**
   * Provides the Twitter provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param tokenSecretProvider The token secret provider implementation.
   * @param configuration The Play configuration.
   * @return The Twitter provider.
   */
  @Provides
  def provideTwitterProvider(
    httpLayer: HTTPLayer,
    tokenSecretProvider: OAuth1TokenSecretProvider,
    configuration: Configuration): TwitterProvider = {

    val settings = configuration.underlying.as[OAuth1Settings]("silhouette.twitter")
    new TwitterProvider(httpLayer, new PlayOAuth1Service(settings), tokenSecretProvider, settings)
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }
}
