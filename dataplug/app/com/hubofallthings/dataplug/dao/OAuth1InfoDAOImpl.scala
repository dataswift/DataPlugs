/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.dao

import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.dal.Tables
import com.hubofallthings.dataplug.utils.Mailer
import javax.inject.{ Inject, Singleton }
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.OAuth1Info
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import org.hatdex.libs.dal.SlickPostgresDriver
import org.hatdex.libs.dal.SlickPostgresDriver.api._
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }

import scala.concurrent.Future

/**
 * The DAO to store the OAuth2 information.
 */
@Singleton
class OAuth1InfoDAOImpl @Inject() (
    protected val dbConfigProvider: DatabaseConfigProvider,
    mailer: Mailer)
  extends DelegableAuthInfoDAO[OAuth1Info] with HasDatabaseConfigProvider[SlickPostgresDriver] {

  import com.hubofallthings.dataplug.dal.ModelTranslation._

  implicit val ec = IoExecutionContext.ioThreadPool

  /**
   * Saves the OAuth2 info.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The OAuth2 info to save.
   * @return The saved OAuth2 info or None if the OAuth2 info couldn't be saved.
   */
  def add(loginInfo: LoginInfo, authInfo: OAuth1Info): Future[OAuth1Info] = {
    val q = Tables.UserOauth1Info += toDbModel(loginInfo, authInfo)

    db.run(q).map(_ => authInfo)
  }

  /**
   * Updates the auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be updated.
   * @param authInfo The auth info to update.
   * @return The updated auth info.
   */
  def update(loginInfo: LoginInfo, authInfo: OAuth1Info): Future[OAuth1Info] = {
    val q = for {
      auth <- Tables.UserOauth1Info if auth.providerId === loginInfo.providerID && auth.userId === loginInfo.providerKey
    } yield (auth.token, auth.secret)

    val action = q.update(authInfo.token, authInfo.secret)

    db.run(action).map(_ => authInfo)
  }

  /**
   * Finds the OAuth2 info which is linked with the specified login info.
   *
   * @param loginInfo The linked login info.
   * @return The retrieved OAuth2 info or None if no OAuth2 info could be retrieved for the given login info.
   */
  def find(loginInfo: LoginInfo): Future[Option[OAuth1Info]] = {
    val q = Tables.UserOauth1Info.filter(auth => auth.providerId === loginInfo.providerID && auth.userId === loginInfo.providerKey)

    db.run(q.result).map(_.headOption.map(fromDbModel))
  }

  /**
   * Saves the auth info for the given login info.
   *
   * This method either adds the auth info if it doesn't exists or it updates the auth info
   * if it already exists.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The auth info to save.
   * @return The saved auth info.
   */
  def save(loginInfo: LoginInfo, authInfo: OAuth1Info): Future[OAuth1Info] = {
    find(loginInfo).flatMap {
      case Some(_) => update(loginInfo, authInfo)
      case None    => add(loginInfo, authInfo)
    }
  }

  /**
   * Removes the auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be removed.
   * @return A future to wait for the process to be completed.
   */
  def remove(loginInfo: LoginInfo): Future[Unit] = {
    val q = Tables.UserOauth1Info.filter(auth => auth.providerId === loginInfo.providerID && auth.userId === loginInfo.providerKey)

    db.run(q.delete).map(_ => Unit)
  }
}
