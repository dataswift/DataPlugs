/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.dao

import akka.Done
import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.dal.Tables
import com.hubofallthings.dataplug.models.User
import javax.inject.{ Inject, Singleton }
import com.mohiva.play.silhouette.api.LoginInfo
import org.hatdex.libs.dal.SlickPostgresDriver
import org.hatdex.libs.dal.SlickPostgresDriver.api._
import org.joda.time.DateTime
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }

import scala.concurrent._

/**
 * Give access to the user object.
 */
@Singleton
class UserDAOImpl @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)
  extends UserDAO with HasDatabaseConfigProvider[SlickPostgresDriver] {

  import com.hubofallthings.dataplug.dal.ModelTranslation._

  implicit val ec = IoExecutionContext.ioThreadPool

  /**
   * Finds a user by its login info.
   *
   * @param loginInfo The login info of the user to find.
   * @return The found user or None if no user for the given login info could be found.
   */
  def find(loginInfo: LoginInfo): Future[Option[User]] = {
    val q = for {
      ((user, _), maybeUserLinkUser) <- Tables.UserUser
        .filter(u => u.providerId === loginInfo.providerID && u.userId === loginInfo.providerKey)
        .joinLeft(Tables.UserLink.filter(ul => ul.masterProviderId === loginInfo.providerID && ul.masterUserId === loginInfo.providerKey).sortBy(_.created.desc).take(1))
        .on((u, l) => u.providerId === l.masterProviderId && u.userId === l.masterUserId)
        .joinLeft(Tables.UserLinkedUser).on((q1, lu) =>
          q1._2.map(_.linkedProviderId) === lu.providerId && q1._2.map(_.linkedUserId) === lu.userId)
    } yield (user, maybeUserLinkUser)

    val foundUsers = db.run(q.result).map(_.map(r => fromDbModel(r._1, r._2)))

    foundUsers.map(fu => {
      fu.toList.groupBy(_.loginInfo).flatMap {
        case (_, users) =>
          val linkedAccounts = users.flatMap(_.linkedUsers)
          users.headOption.map(user => user.copy(linkedUsers = linkedAccounts))
      }.headOption
    })
  }

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: User): Future[User] = {
    val q = for {
      rowsAffected <- Tables.UserUser
        .filter(u => u.providerId === user.providerId && u.userId === user.userId)
        .map(_.userId)
        .update(user.userId)
      result <- rowsAffected match {
        case 0 => Tables.UserUser += Tables.UserUserRow(user.providerId, user.userId, DateTime.now().toLocalDateTime)
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(s"Expected 0 or 1 change, not $n for user ${user.userId}"))
      }
    } yield result

    db.run(q).map(_ => user)
  }

  /**
   * Links two user accounts together (e.g. multi-account login with social credentials)
   *
   * @param mainLoginInfo The login info of the user to link to.
   * @param linkedLoginInfo The login info of the user to link.
   */
  def link(mainLoginInfo: LoginInfo, linkedLoginInfo: LoginInfo): Future[Unit] = {
    val q = for {
      rowsAffected <- Tables.UserLink
        .filter(u => u.masterProviderId === mainLoginInfo.providerID && u.masterUserId === mainLoginInfo.providerKey &&
          u.linkedProviderId === linkedLoginInfo.providerID && u.linkedUserId === linkedLoginInfo.providerKey)
        .map(_.created)
        .update(DateTime.now().toLocalDateTime)
      result <- rowsAffected match {
        case 0 => Tables.UserLink += Tables.UserLinkRow(
          0,
          mainLoginInfo.providerID,
          mainLoginInfo.providerKey,
          linkedLoginInfo.providerID,
          linkedLoginInfo.providerKey,
          DateTime.now().toLocalDateTime)
        case 1 => DBIO.successful(1)
        case n => DBIO.failed(new RuntimeException(s"Expected 0 or 1 change, not $n linking user ${mainLoginInfo.providerID}:${mainLoginInfo.providerKey} to ${linkedLoginInfo.providerID}:${linkedLoginInfo.providerKey}"))
      }
    } yield result

    db.run(q).map(_ => Unit)
  }

  /**
   * Deletes user from relevant user tables.
   *
   * @param phata The phata of the user to delete.
   * @param userId The userId of user to delete.
   * @return Done if success.
   */
  def delete(phata: String, userId: String): Future[Done] = {
    val dateNow = DateTime.now().toLocalDateTime.toString
    val q = DBIO.seq(
      Tables.DataplugUser.filter(_.phata === phata).delete, //phata
      Tables.DataplugUserStatus.filter(_.phata === phata).delete, //phata
      Tables.UserLink.filter(_.masterUserId === phata).delete, //phata
      Tables.UserOauth2Info.filter(_.userId === userId).delete, //user id
      Tables.UserUser.filter(_.userId === userId).delete, // user id
      Tables.LogDataplugUser.filter(_.phata === phata).map(_.message).update(Some(s"Deleted on $dateNow")), // phata
      Tables.UserUser.filter(_.userId === phata).delete) // phata

    db.run(q.transactionally).flatMap { _ =>
      Future.successful(Done)
    }
  }
}
