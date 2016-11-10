package org.hatdex.dataplug.services.dao

import javax.inject.{ Inject, Singleton }

import anorm.SqlParser._
import anorm.{ Macro, ResultSetParser, RowParser, _ }
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import org.hatdex.bulletin.actors.IoExecutionContext
import org.hatdex.dataplug.models.User
import play.api.Logger
import play.api.db.{ DBApi, Database, NamedDatabase }
import play.api.libs.json.JsValue

import scala.concurrent._

/**
 * Give access to the user object.
 */
@Singleton
class UserDAOImpl @Inject() (@NamedDatabase("default") db: Database) extends UserDAO {
  implicit val ec = IoExecutionContext.ioThreadPool

  private def simpleUserInfoParser(table: String): RowParser[User] = {
    get[String](s"$table.provider_id") ~
      get[String](s"$table.user_id") map {
        case providerId ~ userId =>
          User(providerId, userId, List())
      }
  }

  private val simpleUserParser = simpleUserInfoParser("user_user")
  private val linkedUserParser = simpleUserInfoParser("user_linked_user")

  private def linkedUsersParser: RowParser[User] = {
    simpleUserParser ~
      linkedUserParser.? map {
        case mainUser ~ maybeLinkedUser =>
          User(mainUser.providerId, mainUser.userId, maybeLinkedUser.map(List(_)).getOrElse(List()))
      }
  }

  private def singleUserInfoParser: ResultSetParser[User] =
    simpleUserParser.single

  /**
   * Finds a user by its login info.
   *
   * @param loginInfo The login info of the user to find.
   * @return The found user or None if no user for the given login info could be found.
   */
  def find(loginInfo: LoginInfo) =
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          val foundUsers = SQL(
            """
              | SELECT * FROM user_user
              | LEFT JOIN user_link
              |   ON user_link.master_provider_id = user_user.provider_id
              |   AND user_link.master_user_id = user_user.user_id
              | LEFT JOIN user_linked_user
              |   ON user_link.linked_provider_id = user_linked_user.provider_id
              |   AND user_link.linked_user_id = user_linked_user.user_id
              | WHERE
              |   user_user.provider_id = {providerId}
              |   AND user_user.user_id = {userId}
            """.stripMargin)
            .on(
              'providerId -> loginInfo.providerID,
              'userId -> loginInfo.providerKey)
            .as(linkedUsersParser.*)

          foundUsers.groupBy(_.loginInfo).flatMap {
            case (_, users) =>
              val linkedAccounts = users.flatMap(_.linkedUsers)
              users.headOption.map(user => user.copy(linkedUsers = linkedAccounts))
          }.headOption
        }
      }
    }

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: User) = {
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | INSERT INTO user_user (provider_id, user_id)
              | VALUES ({providerId}, {userId})
              | ON CONFLICT (provider_id, user_id) DO UPDATE SET
              |   user_id = {userId}
            """.stripMargin)
            .on(
              'providerId -> user.providerId,
              'userId -> user.userId)
            .executeInsert(singleUserInfoParser)
        }
      }
    }
  }

  /**
   * Links two user accounts together (e.g. multi-account login with social credentials)
   *
   * @param mainLoginInfo The login info of the user to link to.
   * @param linkedLoginInfo The login info of the user to link.
   */
  def link(mainLoginInfo: LoginInfo, linkedLoginInfo: LoginInfo): Future[Unit] = {
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | INSERT INTO user_link (master_provider_id, master_user_id, linked_provider_id, linked_user_id)
              | VALUES ({providerId}, {userId}, {linkedProviderId}, {linkedUserId})
              | ON CONFLICT DO NOTHING
            """.stripMargin)
            .on(
              'providerId -> mainLoginInfo.providerID,
              'userId -> mainLoginInfo.providerKey,
              'linkedProviderId -> linkedLoginInfo.providerID,
              'linkedUserId -> linkedLoginInfo.providerKey)
            .executeInsert()
        }
      }
    }
  }
}
