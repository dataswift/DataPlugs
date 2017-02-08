package org.hatdex.dataplug.dao

import javax.inject.{ Inject, Singleton }

import anorm._
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.OAuth1Info
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.utils.Mailer
import play.api.db.{ Database, _ }

import scala.concurrent.{ Future, blocking }

/**
 * The DAO to store the OAuth2 information.
 */
@Singleton
class OAuth1InfoDAOImpl @Inject() (@NamedDatabase("default") db: Database, mailer: Mailer) extends DelegableAuthInfoDAO[OAuth1Info] {
  implicit val ec = IoExecutionContext.ioThreadPool

  private def loginInfoParser(table: String): RowParser[LoginInfo] =
    Macro.parser[LoginInfo](
      s"$table.provider_id",
      s"$table.user_id")

  private def singleLoginInfoParser(table: String): ResultSetParser[LoginInfo] =
    loginInfoParser(table).single

  private def oauth1InfoParser: RowParser[OAuth1Info] = Macro.namedParser[OAuth1Info]

  /**
   * Saves the OAuth2 info.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The OAuth2 info to save.
   * @return The saved OAuth2 info or None if the OAuth2 info couldn't be saved.
   */
  def add(loginInfo: LoginInfo, authInfo: OAuth1Info): Future[OAuth1Info] = {
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | INSERT INTO user_oauth1_info
              |   (provider_id, user_id, token, secret)
              | VALUES
              |   ({providerId}, {userId}, {token}, {secret})
            """.stripMargin)
            .on(
              'providerId -> loginInfo.providerID,
              'userId -> loginInfo.providerKey,
              'token -> authInfo.token,
              'secret -> authInfo.secret)
            .executeInsert(singleLoginInfoParser("user_oauth1_info"))
          authInfo
        }
      }
    }
  }

  /**
   * Updates the auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be updated.
   * @param authInfo The auth info to update.
   * @return The updated auth info.
   */
  def update(loginInfo: LoginInfo, authInfo: OAuth1Info): Future[OAuth1Info] = {
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | UPDATE user_oauth1_info
              |   SET
              |     token = {token},
              |     secret = {secret}
              | WHERE
              |   provider_id = {providerId}
              |   AND user_id = {userId}
            """.stripMargin)
            .on(
              'providerId -> loginInfo.providerID,
              'userId -> loginInfo.providerKey,
              'token -> authInfo.token,
              'secret -> authInfo.secret)
            .executeInsert(singleLoginInfoParser("user_oauth1_info"))
          authInfo
        }
      }
    }
  }

  /**
   * Finds the OAuth2 info which is linked with the specified login info.
   *
   * @param loginInfo The linked login info.
   * @return The retrieved OAuth2 info or None if no OAuth2 info could be retrieved for the given login info.
   */
  def find(loginInfo: LoginInfo): Future[Option[OAuth1Info]] = {
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | SELECT * FROM user_oauth1_info
              | WHERE
              |   provider_id = {providerId}
              |   AND user_id = {userId}
            """.stripMargin)
            .on(
              'providerId -> loginInfo.providerID,
              'userId -> loginInfo.providerKey)
            .as(oauth1InfoParser.singleOpt)
        }
      }
    }
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
    mailer.serverExceptionNotifyInternal(s"Deleting oauth1 token", new RuntimeException(s"Deleting oauth1 token for $loginInfo"))
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | DELETE FROM user_oauth1_info
              |   WHERE
              |   provider_id = {providerId}
              |   AND user_id = {userId}
            """.stripMargin)
            .on(
              'providerId -> loginInfo.providerID,
              'userId -> loginInfo.providerKey)
            .executeUpdate()
        }
      }
    }
  }
}
