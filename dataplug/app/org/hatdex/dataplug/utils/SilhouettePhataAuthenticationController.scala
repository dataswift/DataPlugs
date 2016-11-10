/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.utils

import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.api.{ Environment, Silhouette }
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import net.ceedubs.ficus.Ficus._
import org.hatdex.dataplug.models.User
import org.joda.time.DateTime
import play.api.i18n.I18nSupport
import play.api.mvc.Controller
import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

abstract class SilhouettePhataAuthenticationController(
    silhouette: Silhouette[PhataAuthenticationEnvironment],
    clock: Clock,
    configuration: Configuration) extends Controller with I18nSupport {

  def env: Environment[PhataAuthenticationEnvironment] = silhouette.env

  def SecuredAction = silhouette.SecuredAction
  def UnsecuredAction = silhouette.UnsecuredAction
  def UserAwareAction = silhouette.UserAwareAction

  implicit def securedRequest2User[A](implicit request: SecuredRequest[PhataAuthenticationEnvironment, A]): User = request.identity
  implicit def userAwareRequest2UserOpt[A](implicit request: UserAwareRequest[PhataAuthenticationEnvironment, A]): Option[User] = request.identity

  def authenticatorWithRememberMe(authenticator: CookieAuthenticator, rememberMe: Boolean): CookieAuthenticator = {
    if (rememberMe) {
      val expirationTime: DateTime = clock.now + rememberMeParams._1
      authenticator.copy(
        expirationDateTime = expirationTime,
        idleTimeout = rememberMeParams._2,
        cookieMaxAge = rememberMeParams._3)
    }
    else {
      authenticator
    }
  }

  private lazy val rememberMeParams: (FiniteDuration, Option[FiniteDuration], Option[FiniteDuration]) = {
    val cfg = configuration.getConfig("silhouette.authenticator.rememberMe").get.underlying
    (
      cfg.as[FiniteDuration]("authenticatorExpiry"),
      cfg.getAs[FiniteDuration]("authenticatorIdleTimeout"),
      cfg.getAs[FiniteDuration]("cookieMaxAge"))
  }
}