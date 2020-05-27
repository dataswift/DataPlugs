/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.services

import akka.Done
import com.hubofallthings.dataplug.dao.UserDAO
import com.hubofallthings.dataplug.models.User
import javax.inject.Inject
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Handles actions to users.
 *
 * @param userDAO The user DAO implementation.
 */
class UserServiceImpl @Inject() (userDAO: UserDAO)(implicit ec: ExecutionContext) extends UserService {

  /**
   * Retrieves a user that matches the specified login info.
   *
   * @param loginInfo The login info to retrieve a user.
   * @return The retrieved user or None if no user could be retrieved for the given login info.
   */
  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = userDAO.find(loginInfo)

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: User) = userDAO.save(user)

  /**
   * Saves the social profile for a user.
   *
   * If a user exists for this profile then update the user, otherwise create a new user with the given profile.
   *
   * @param profile The social profile to save.
   * @return The user for whom the profile was saved.
   */
  def save(profile: CommonSocialProfile) = {
    userDAO.find(profile.loginInfo).flatMap {
      case Some(user) => // Update user with profile
        userDAO.save(user.copy(
          providerId = profile.loginInfo.providerID,
          userId = profile.loginInfo.providerKey))
      case None => // Insert a new user
        userDAO.save(User(
          providerId = profile.loginInfo.providerID,
          userId = profile.loginInfo.providerKey,
          List()))
    }
  }

  /**
   * Links two user accounts together (e.g. multi-account login with social credentials)
   *
   * @param mainUser The login info of the main user.
   * @param linkedUser The login info of the user to link.
   */
  def link(mainUser: User, linkedUser: User) = {
    userDAO.link(mainUser.loginInfo, linkedUser.loginInfo)
  }

  /**
   * Deletes user from relevant user tables.
   *
   * @param phata The phata of the user to delete.
   * @param userId The userId of user to delete.
   * @return Done if success.
   */
  def delete(phata: String, userId: String): Future[Done] = {
    userDAO.delete(phata, userId)
  }
}