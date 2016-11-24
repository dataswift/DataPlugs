package org.hatdex.dataplug.dao

import org.hatdex.dataplug.models.User
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.matcher.MatchResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.db.{ Database, Databases }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class UserDAOImplSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {

  def awaiting[T]: Future[MatchResult[T]] => Result = { _.await }

  def withMyDatabase[T](block: Database => T) = {
    Databases.withDatabase(
      driver = "org.postgresql.Driver",
      url = "jdbc:postgresql://localhost/dataplug",
      name = "dataplug",
      config = Map(
        "user" -> "dataplug",
        "password" -> "secret"
      )
    )(block)
  }

  sequential

  "User DAO" should {
    val userAccount = User("test", "userAccount", List())
    val userSocialAccount = User("testsocial", "userAccount2", List())
    val userAnotherSocialAccount = User("testanothersocial", "userAccount2", List())

    "Save new users" in {
      withMyDatabase { database =>
        val userDao = new UserDAOImpl(database)

        val result = for {
          savedUser <- userDao.save(userAccount)
        } yield {
          savedUser.userId must be equalTo ("userAccount")
        }

        result await (1, 10.seconds)
      }
    }

    "Link users profiles" in {
      withMyDatabase { database =>
        val userDao = new UserDAOImpl(database)
        val result = for {
          savedUser <- userDao.save(userAccount)
          savedSocialUser <- userDao.save(userSocialAccount)
          _ <- userDao.link(savedUser.loginInfo, savedSocialUser.loginInfo)
          savedAnotherSocialUsers <- userDao.save(userAnotherSocialAccount)
          _ <- userDao.link(savedUser.loginInfo, savedAnotherSocialUsers.loginInfo)
          linkedUser <- userDao.find(savedUser.loginInfo)
        } yield {
          savedUser.userId must be equalTo ("userAccount")
          savedSocialUser.userId must be equalTo ("userAccount2")
          linkedUser must beSome
          linkedUser.get.userId must be equalTo ("userAccount")
          linkedUser.get.linkedUsers.length must be equalTo (2)
        }

        result await (1, 10.seconds)
      }
    }
  }
}
