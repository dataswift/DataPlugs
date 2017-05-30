package org.hatdex.dataplugCalendar

import akka.util.Timeout
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import org.hatdex.dataplug.apiInterfaces.models.{ DataPlugFetchContinuation, DataPlugFetchNextSync }
import org.hatdex.dataplug.models.User
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.testkit.{ DataPlugEndpointInterfaceTestHelper, TestModule }
import org.hatdex.dataplugCalendar.apiInterfaces.GoogleCalendarInterface
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Logger }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class GoogleCalendarInterfaceSpec(implicit val ee: ExecutionEnv) extends Specification with DataPlugEndpointInterfaceTestHelper with BeforeAfterAll {

  val injector = new GuiceApplicationBuilder()
    .loadConfig(env => { Logger.info(s"Loading config for env $env"); Configuration.load(env) })
    .overrides(new TestModule)
    .overrides(bind[WSClient].toInstance(client))
    .build
    .injector

  sequential

  override def beforeAll: Unit = {
    val userService = injector.instanceOf[UserService]
    val authInfoRepository = injector.instanceOf[AuthInfoRepository]
    val configuration = injector.instanceOf[Configuration]

    val provider = "google"
    val hat = configuration.underlying.getString(s"testAccount.$provider.hat")
    val userId = configuration.underlying.getString(s"testAccount.$provider.userID")
    val accessToken = configuration.underlying.getString(s"testAccount.$provider.accessToken")
    val refreshToken = configuration.underlying.getString(s"testAccount.$provider.refreshToken")

    userService.save(User("hatlogin", hat, List(User(provider, userId, List()))))
    authInfoRepository.add[OAuth2Info](
      LoginInfo(provider, userId),
      OAuth2Info(accessToken, Some("Bearer"), Some(3599), Some(refreshToken), None))
  }

  override def afterAll: Unit = {
    client.close()
  }

  "Google Calendar Interface" should {
    implicit val timeout: Timeout = 10.seconds
    "Fetch data" in {
      val interface = injector.instanceOf[GoogleCalendarInterface]
      interface.buildFetchParameters(None) flatMap { apiCall =>
        interface.fetch(apiCall, "test.hubofallthings.net", null) map { nextStep =>
          nextStep must beAnInstanceOf[DataPlugFetchContinuation]
        }
      } awaitFor 5.seconds

    }

    "Fetch continuations" in {
      val interface = injector.instanceOf[GoogleCalendarInterface]
      interface.buildFetchParameters(None) flatMap { apiCall =>
        interface.fetch(apiCall, "test.hubofallthings.net", null) map { nextStep =>
          nextStep must beAnInstanceOf[DataPlugFetchContinuation]
          nextStep match {
            case DataPlugFetchContinuation(nextApiCall) => nextApiCall
            case DataPlugFetchNextSync(nextApiCall)     => nextApiCall
          }
        }
      } flatMap { apiCall =>
        interface.fetch(apiCall, "test.hubofallthings.net", null) map { nextStep =>
          nextStep must beAnInstanceOf[DataPlugFetchContinuation]
        }
      } awaitFor 5.seconds

    }
  }
}
