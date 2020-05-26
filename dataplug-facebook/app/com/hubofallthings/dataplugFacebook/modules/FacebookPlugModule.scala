/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplugFacebook.modules

import akka.actor.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Provides }
import com.hubofallthings.dataplug.actors.DataPlugManagerActor
import com.hubofallthings.dataplug.apiInterfaces.authProviders.HatOAuth2Provider
import com.hubofallthings.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry, DataPlugRegistry }
import com.hubofallthings.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import com.hubofallthings.dataplug.dal.SchemaMigrationImpl
import com.hubofallthings.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl, DataPlugSharedNotableDAO, DataPlugSharedNotableDAOImpl }
import com.hubofallthings.dataplug.services.{ DataPlugEndpointService, DataPlugEndpointServiceImpl, DataPlugNotablesService, DataPlugNotablesServiceImpl, StartupService, StartupServiceImpl }
import com.hubofallthings.dataplugFacebook.apiInterfaces._
import com.hubofallthings.dataplugFacebook.apiInterfaces.authProviders._
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
class FacebookPlugModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

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
    bind[HatOAuth2Provider].to[FacebookProvider]

    //    bindActorFactory[InjectedHatClientActor, InjectedHatClientActor.Factory]
    bindActor[DataPlugManagerActor]("dataplug-manager")
  }

  /**
   * Provides the social provider registry.
   *
   * @param facebookProfileInterface The Facebook profile API endpoint implementation, injected
   * @param facebookEventInterface The Facebook events API endpoint implementation, injected
   * @param facebookFeedInterface The Facebook feed API endpoint implementation, injected
   * @return The DataPlugRegistry.
   */
  @Provides
  def provideDataPlugCollection(
    facebookProfileInterface: FacebookProfileInterface,
    facebookProfilePictureInterface: FacebookProfilePictureInterface,
    facebookEventInterface: FacebookEventInterface,
    facebookFeedInterface: FacebookFeedInterface,
    facebookPostsInterface: FacebookPostsInterface,
    facebookUserLikesInterface: FacebookUserLikesInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(
      facebookProfileInterface,
      facebookProfilePictureInterface,
      facebookEventInterface,
      facebookFeedInterface,
      facebookPostsInterface,
      facebookUserLikesInterface))
  }

  @Provides
  def provideDataPlugEndpointChoiceCollection(
    facebookProvider: FacebookProvider,
    facebookProfileCheck: FacebookProfileCheck): DataPlugOptionsCollectorRegistry = {

    val variants: Seq[(Provider, DataPlugOptionsCollector)] = Seq((facebookProvider, facebookProfileCheck))
    DataPlugOptionsCollectorRegistry(variants)
  }

  /**
   * Provides the social provider registry.
   *
   * @param facebookProvider The Facebook provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(
    facebookProvider: FacebookProvider): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      facebookProvider))
  }

  /**
   * Provides the Fitbit provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @param configuration The Play configuration.
   * @return The Facebook provider.
   */
  @Provides
  def provideFacebookProvider(
    httpLayer: HTTPLayer,
    stateProvider: SocialStateHandler,
    configuration: Configuration): FacebookProvider = {
    new FacebookProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.facebook"))
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }
}