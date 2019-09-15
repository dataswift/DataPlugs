/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.apiInterfaces.authProviders

import com.hubofallthings.dataplug.testkit.{DataPlugEndpointInterfaceTestHelper, TestModule}
import com.hubofallthings.dataplug.apiInterfaces.authProviders.OAuth2TokenHelper
import com.hubofallthings.dataplug.models.User
import com.hubofallthings.dataplug.services.UserService
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import com.hubofallthings.dataplug.testkit.DataPlugEndpointInterfaceTestHelper
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import play.api.Configuration
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class OAuth2TokenHelperSpec(implicit val ee: ExecutionEnv) extends Specification with DataPlugEndpointInterfaceTestHelper with BeforeAfterAll {

  val injector = new GuiceApplicationBuilder()
    .loadConfig(env => Configuration.load(env))
    .overrides(new TestModule)
    .overrides(bind[WSClient].toInstance(client))
    .build
    .injector

  sequential

  override def beforeAll: Unit = {
    val userService = injector.instanceOf[UserService]
    val authInfoRepository = injector.instanceOf[AuthInfoRepository]
    userService.save(User("hatlogin", "test.hubofallthings.net", List(User("google", "107397297243742920250", List()))))
    authInfoRepository.add[OAuth2Info](
      LoginInfo("google", "107397297243742920250"),
      OAuth2Info(
        "ya29.CjCeA6Ft6ixWPdSaxtFNGTAQ8Tz2OdyImSimUjMtzq1rnicDCsns4wJiKYapIkkGKeU",
        Some("Bearer"), Some(3599),
        Some("1/ciG7g-0unyey60iA_kwlhOrcHcw3CFCxxyEhr9-v4Qg"), None))
  }

  override def afterAll: Unit = {
    client.close()
  }

  "Token Helper" should {

    "Refresh auth token" in {
      val tokenHelper = injector.instanceOf[OAuth2TokenHelper]
      val authInfoRepository = injector.instanceOf[AuthInfoRepository]
      val userService = injector.instanceOf[UserService]

      val eventualUser = userService.retrieve(LoginInfo("hatlogin", "test.hubofallthings.net")) map { maybeUser =>
        maybeUser must beSome
        maybeUser.get
      }

      val eventualOauthInfo = eventualUser flatMap { user =>
        val googleUser = user.linkedUsers.find(_.providerId == "google")
        googleUser must beSome
        val eventualMaybeOAuthInfo = authInfoRepository.find[OAuth2Info](googleUser.get.loginInfo)
        eventualMaybeOAuthInfo map { maybeOAuth =>
          maybeOAuth must beSome
          (googleUser.get, maybeOAuth.get)
        }
      }

      eventualOauthInfo flatMap {
        case (user, oauth2Info) =>
          oauth2Info.refreshToken must beSome
          val refreshedToken = tokenHelper.refresh(user.loginInfo, oauth2Info.refreshToken.get)
          refreshedToken must beSome
          refreshedToken.get map { token =>
            val refreshedToken = oauth2Info.copy(accessToken = token.accessToken, expiresIn = token.expiresIn)
            refreshedToken.refreshToken must beSome
            authInfoRepository.save[OAuth2Info](user.loginInfo, refreshedToken)
            refreshedToken.expiresIn must beSome
          }
      } awaitFor 5.seconds
    }
  }

}
