package org.hatdex.dataplug.dao

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import org.hatdex.dataplug.models.User

import scala.concurrent.Future

/**
 * Give access to the user object.
 */
trait UserDAO {

  /**
   * Finds a user by its login info.
   *
   * @param loginInfo The login info of the user to find.
   * @return The found user or None if no user for the given login info could be found.
   */
  def find(loginInfo: LoginInfo): Future[Option[User]]

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: User): Future[User]

  /**
   * Links two user accounts together (e.g. multi-account login with social credentials)
   *
   * @param mainLoginInfo The login info of the user to link to.
   * @param linkedLoginInfo The login info of the user to link.
   */
  def link(mainLoginInfo: LoginInfo, linkedLoginInfo: LoginInfo): Future[Unit]
}