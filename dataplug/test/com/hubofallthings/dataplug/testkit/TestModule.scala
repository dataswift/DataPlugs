/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.testkit

import javax.inject.Named
import akka.actor.{ActorSystem, Scheduler}
import com.google.inject.{AbstractModule, Provides}
import com.hubofallthings.dataplug.dao.UserDAO
import com.hubofallthings.dataplug.services.{UserService, UserServiceImpl}
import com.hubofallthings.dataplug.utils.{JwtIdentityVerification, PhataAuthenticationEnvironment}
import com.mohiva.play.silhouette.api.crypto.{Crypter, CrypterAuthenticatorEncoder}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.{PlayHTTPLayer, _}
import com.mohiva.play.silhouette.api.{Environment, EventBus}
import com.mohiva.play.silhouette.crypto.{JcaCookieSigner, JcaCookieSignerSettings, JcaCrypter, JcaCrypterSettings}
import com.mohiva.play.silhouette.impl.authenticators.{CookieAuthenticator, CookieAuthenticatorService, CookieAuthenticatorSettings}
import com.mohiva.play.silhouette.impl.providers._
import com.hubofallthings.dataplugCalendar.apiInterfaces.authProviders._
import com.mohiva.play.silhouette.impl.providers.oauth2.state.{CookieStateProvider, CookieStateSettings}
import com.mohiva.play.silhouette.impl.util.{DefaultFingerprintGenerator, SecureRandomIDGenerator}
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.daos.{DelegableAuthInfoDAO, InMemoryAuthInfoDAO}
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global

class TestModule extends AbstractModule with ScalaModule {

  def configure() = {
    bind[JwtIdentityVerification].to[IdentityVerificationTestImpl]
    //    bind[Silhouette[PhataAuthenticationEnvironment]].to[SilhouetteProvider[PhataAuthenticationEnvironment]]
    bind[UserService].to[UserServiceImpl]
    bind[UserDAO].to[UserDAOTestImpl]
    //    bind[CacheLayer].to[PlayCacheLayer]
    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher)
    bind[FingerprintGenerator].toInstance(new DefaultFingerprintGenerator(false))
    bind[EventBus].toInstance(EventBus())
    bind[Clock].toInstance(Clock())
    bind[DelegableAuthInfoDAO[OAuth1Info]].toInstance(new InMemoryAuthInfoDAO[OAuth1Info])
    bind[DelegableAuthInfoDAO[OAuth2Info]].toInstance(new InMemoryAuthInfoDAO[OAuth2Info])
  }
  @Provides
  def provideAuthInfoRepository(
    oauth1InfoDAO: DelegableAuthInfoDAO[OAuth1Info],
    oauth2InfoDAO: DelegableAuthInfoDAO[OAuth2Info]): AuthInfoRepository = {

    new DelegableAuthInfoRepository(oauth1InfoDAO, oauth2InfoDAO)
  }

  /**
   * Provides the HTTP layer implementation.
   *
   * @param client Play's WS client.
   * @return The HTTP layer implementation.
   */
  @Provides
  def provideHTTPLayer(client: WSClient): HTTPLayer = new PlayHTTPLayer(client)

  /**
   * Provides the authenticator service.
   *
   * @param cookieSigner The cookie signer implementation.
   * @param crypter The crypter implementation.
   * @param fingerprintGenerator The fingerprint generator implementation.
   * @param idGenerator The ID generator implementation.
   * @param configuration The Play configuration.
   * @param clock The clock instance.
   * @return The authenticator service.
   */
  @Provides
  def provideAuthenticatorService(
    @Named("authenticator-cookie-signer") cookieSigner: CookieSigner,
    @Named("authenticator-crypter") crypter: Crypter,
    fingerprintGenerator: FingerprintGenerator,
    idGenerator: IDGenerator,
    configuration: Configuration,
    clock: Clock): AuthenticatorService[CookieAuthenticator] = {

    val config = configuration.underlying.as[CookieAuthenticatorSettings]("silhouette.authenticator")
    val encoder = new CrypterAuthenticatorEncoder(crypter)

    new CookieAuthenticatorService(config, None, cookieSigner, encoder, fingerprintGenerator, idGenerator, clock)
  }

  /**
   * Provides the cookie signer for the authenticator.
   *
   * @param configuration The Play configuration.
   * @return The cookie signer for the authenticator.
   */
  @Provides @Named("authenticator-cookie-signer")
  def provideAuthenticatorCookieSigner(configuration: Configuration): CookieSigner = {
    val config = configuration.underlying.as[JcaCookieSignerSettings]("silhouette.authenticator.cookie.signer")

    new JcaCookieSigner(config)
  }

  /**
   * Provides the crypter for the authenticator.
   *
   * @param configuration The Play configuration.
   * @return The crypter for the authenticator.
   */
  @Provides @Named("authenticator-crypter")
  def provideAuthenticatorCrypter(configuration: Configuration): Crypter = {
    val config = configuration.underlying.as[JcaCrypterSettings]("silhouette.authenticator.crypter")

    new JcaCrypter(config)
  }

  /**
   * Provides the cookie signer for the OAuth2 state provider.
   *
   * @param configuration The Play configuration.
   * @return The cookie signer for the OAuth2 state provider.
   */
  @Provides @Named("oauth2-state-cookie-signer")
  def provideOAuth2StageCookieSigner(configuration: Configuration): CookieSigner = {
    val config = configuration.underlying.as[JcaCookieSignerSettings]("silhouette.oauth2StateProvider.cookie.signer")

    new JcaCookieSigner(config)
  }

  /**
   * Provides the Silhouette environment.
   *
   * @param userService The user service implementation.
   * @param authenticatorService The authentication service implementation.
   * @param eventBus The event bus instance.
   * @return The Silhouette environment.
   */
  @Provides
  def provideEnvironment(
    userService: UserService,
    authenticatorService: AuthenticatorService[CookieAuthenticator],
    eventBus: EventBus): Environment[PhataAuthenticationEnvironment] = {

    Environment[PhataAuthenticationEnvironment](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }

  /**
   * Provides the social provider registry.
   *
   * @param facebookProvider The Facebook provider implementation.
   * @param googleProvider The Google provider implementation.
   * @param vkProvider The VK provider implementation.
   * @param clefProvider The Clef provider implementation.
   * @param twitterProvider The Twitter provider implementation.
   * @param xingProvider The Xing provider implementation.
   * @param yahooProvider The Yahoo provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(
    googleProvider: GoogleProvider): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      googleProvider
    ))
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
    stateProvider: OAuth2StateProvider,
    configuration: Configuration): GoogleProvider = {

    new GoogleProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.google"))
  }

  /**
   * Provides the OAuth2 state provider.
   *
   * @param idGenerator The ID generator implementation.
   * @param cookieSigner The cookie signer implementation.
   * @param configuration The Play configuration.
   * @param clock The clock instance.
   * @return The OAuth2 state provider implementation.
   */
  @Provides
  def provideOAuth2StateProvider(
    idGenerator: IDGenerator,
    @Named("oauth2-state-cookie-signer") cookieSigner: CookieSigner,
    configuration: Configuration, clock: Clock): OAuth2StateProvider = {

    val settings = configuration.underlying.as[CookieStateSettings]("silhouette.oauth2StateProvider")
    new CookieStateProvider(settings, idGenerator, cookieSigner, clock)
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }
}
