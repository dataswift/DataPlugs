package org.hatdex.dataplug.dao

import javax.inject.{ Inject, Singleton }

import anorm.SqlParser._
import anorm.{ RowParser, ~, _ }
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import org.hatdex.dataplug.actors.IoExecutionContext
import play.api.db.{ Database, _ }
import play.api.libs.json.{ JsValue, Json }

import scala.concurrent._

/**
 * The DAO to store the OAuth2 information.
 */
@Singleton
class OAuth2InfoDAOImpl @Inject() (@NamedDatabase("default") db: Database) extends DelegableAuthInfoDAO[OAuth2Info] {
  implicit val ec = IoExecutionContext.ioThreadPool

  private def loginInfoParser(table: String): RowParser[LoginInfo] =
    Macro.parser[LoginInfo](
      s"$table.provider_id",
      s"$table.user_id")

  private def singleLoginInfoParser(table: String): ResultSetParser[LoginInfo] =
    loginInfoParser(table).single

  import org.hatdex.dataplug.utils.AnormParsers._

  private def oauth2InfoParser: RowParser[OAuth2Info] = {
    get[String]("access_token") ~
      get[Option[String]]("token_Type") ~
      get[Option[Int]]("expires_in") ~
      get[Option[String]]("refresh_token") ~
      get[Option[JsValue]]("params") map {
        case accessToken ~ tokenType ~ expiresIn ~ refreshToken ~ params =>
          OAuth2Info(accessToken, tokenType, expiresIn, refreshToken, params.map(_.as[Map[String, String]]))
      }
  }

  /**
   * Saves the OAuth2 info.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The OAuth2 info to save.
   * @return The saved OAuth2 info or None if the OAuth2 info couldn't be saved.
   */
  def add(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] = {
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | INSERT INTO user_oauth2_info
              |   (provider_id, user_id, access_token, token_type, expires_in, refresh_token, params)
              | VALUES
              |   ({providerId}, {userId}, {accessToken}, {tokenType}, {expiresIn}, {refreshToken}, {params}::json)
            """.stripMargin)
            .on(
              'providerId -> loginInfo.providerID,
              'userId -> loginInfo.providerKey,
              'accessToken -> authInfo.accessToken,
              'tokenType -> authInfo.tokenType,
              'expiresIn -> authInfo.expiresIn,
              'refreshToken -> authInfo.refreshToken,
              'params -> authInfo.params.map(params => Json.toJson(params).toString()))
            .executeInsert(singleLoginInfoParser("user_oauth2_info"))
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
  def update(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] = {
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | UPDATE user_oauth2_info
              |   SET
              |     access_token = {accessToken},
              |     token_type = {tokenType},
              |     expires_in = {expiresIn},
              |     refresh_token = {refreshToken},
              |     params = {params}::json
              | WHERE
              |   provider_id = {providerId}
              |   AND user_id = {userId}
            """.stripMargin)
            .on(
              'providerId -> loginInfo.providerID,
              'userId -> loginInfo.providerKey,
              'accessToken -> authInfo.accessToken,
              'tokenType -> authInfo.tokenType,
              'expiresIn -> authInfo.expiresIn,
              'refreshToken -> authInfo.refreshToken,
              'params -> authInfo.params.map(params => Json.toJson(params).toString()))
            .executeInsert(singleLoginInfoParser("user_oauth2_info"))
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
  def find(loginInfo: LoginInfo): Future[Option[OAuth2Info]] = {
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | SELECT * FROM user_oauth2_info
              | WHERE
              |   provider_id = {providerId}
              |   AND user_id = {userId}
            """.stripMargin)
            .on(
              'providerId -> loginInfo.providerID,
              'userId -> loginInfo.providerKey)
            .as(oauth2InfoParser.singleOpt)
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
  def save(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] = {
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
    Future {
      blocking {
        db.withTransaction { implicit connection =>
          SQL(
            """
              | DELETE FROM user_oauth2_info
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
