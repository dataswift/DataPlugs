/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.testkit

import com.mohiva.play.silhouette.api.LoginInfo

import scala.collection.mutable
import scala.concurrent.Future
import UserDAOTestImpl._
import akka.Done
import com.hubofallthings.dataplug.dao.UserDAO
import com.hubofallthings.dataplug.models.User

/**
 * Give access to the user object.
 */
class UserDAOTestImpl extends UserDAO {

  /**
   * Finds a user by its login info.
   *
   * @param loginInfo The login info of the user to find.
   * @return The found user or None if no user for the given login info could be found.
   */
  def find(loginInfo: LoginInfo) = Future.successful(
    users.find { case (id, user) => user.loginInfo == loginInfo }.map(_._2))

  /**
   * Finds a user by its user ID.
   *
   * @param userID The ID of the user to find.
   * @return The found user or None if no user for the given ID could be found.
   */
  def find(userID: String) = Future.successful(users.get(userID))

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: User) = {
    users += (user.userId -> user)
    Future.successful(user)
  }

  def link(mainLoginInfo: LoginInfo, linkedLoginInfo: LoginInfo): Future[Unit] = {
    val mainUser = users.get(mainLoginInfo.providerID)
    val linkedUser = users.get(linkedLoginInfo.providerID)
    (mainUser, linkedUser) match {
      case (Some(user), Some(linked)) =>
        Future.successful(user.copy(linkedUsers = linked :: user.linkedUsers))
      case _ =>
        Future.successful(())
    }
  }

  override def delete(phata: String, userId: String): Future[Done] = {
    users.remove(userId)
    Future.successful(Done)
  }
}

/**
 * The companion object.
 */
object UserDAOTestImpl {

  /**
   * The list of users.
   */
  val users: mutable.HashMap[String, User] = mutable.HashMap()
}
