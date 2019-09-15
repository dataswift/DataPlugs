package com.hubofallthings.dataplugCalendar

import com.hubofallthings.dataplug.testkit.{DataPlugEndpointInterfaceTestHelper, TestModule}
import com.hubofallthings.dataplug.models.User
import com.hubofallthings.dataplug.services.UserService
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import play.api.{Configuration, Logger}
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class GoogleCalendarListSpec(implicit val ee: ExecutionEnv) extends Specification with DataPlugEndpointInterfaceTestHelper with BeforeAfterAll {

  val injector = new GuiceApplicationBuilder()
    .loadConfig(env => Configuration.load(env))
    .overrides(new TestModule)
    .overrides(bind[WSClient].toInstance(client))
    .build
    .injector

  sequential

  override def beforeAll: Unit = {
    val userService = injector.instanceOf[UserService]
    val authInfoRepository = injector.instanceOf[AuthInfoRepository]
    userService.save(User("hatlogin", "test.hubofallthings.net", List(User("google", "107397297243742920250", List()))))
    authInfoRepository.add[OAuth2Info](
      LoginInfo("google", "107397297243742920250"),
      OAuth2Info(
        "ya29.CjCeA6Ft6ixWPdSaxtFNGTAQ8Tz2OdyImSimUjMtzq1rnicDCsns4wJiKYapIkkGKeU",
        Some("Bearer"), Some(3599),
        Some("1/ciG7g-0unyey60iA_kwlhOrcHcw3CFCxxyEhr9-v4Qg"), None))
  }

  override def afterAll: Unit = {
    client.close()
  }

  "Google Calendar List" should {
    "List calendars" in {
      val interface = injector.instanceOf[GoogleCalendarList]
      interface.buildFetchParameters(None) flatMap { apiCall =>
        interface.get(apiCall, "test.hubofallthings.net", null) map { result =>
          Logger.info(s"Got response ${result}")
          result.toString() must be_!=("")
        }
      } awaitFor 5.seconds

    }
  }

}
