//package org.hatdex.dataplug.apiInterfaces
//
//import akka.actor.ActorSystem
//import akka.stream.ActorMaterializer
//import org.hatdex.dataplug.dao.{ OAuth2InfoDAOImpl, UserDAOImpl }
//import org.hatdex.dataplug.services.UserServiceImpl
//import org.hatdex.dataplugCalendar.apiInterfaces.GoogleCalendarInterface
//import org.specs2.concurrent.ExecutionEnv
//import org.specs2.execute.Result
//import org.specs2.matcher.MatchResult
//import org.specs2.mutable.Specification
//import org.specs2.specification.AfterAll
//import play.api.Logger
//import play.api.db.{ Database, Databases }
//import play.api.libs.json.JsValue
//import play.api.libs.ws.ahc.AhcWSClient
//
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.{ ExecutionContext, Future }
//import scala.concurrent.duration._
//
//class DataPlugEndpointInterfaceSpec(implicit ee: ExecutionEnv) extends Specification with AfterAll with DataPlugEndpointInterface {
//  val sourceName = "testSource"
//  val endpointName = "testEndpoint"
//  val quietTranslationErrors = false
//  val refreshInterval = 5.seconds
//  val logger = Logger("DataPlugEndpointInterface")
//  val defaultApiEndpoint = ApiEndpointCall("", "", ApiEndpointMethod.Get("Get"), Map(), Map(), Map())
//
//  def buildFetchParameters(params: Option[ApiEndpointCall])(implicit ec: ExecutionContext): Future[ApiEndpointCall] = Future.successful(params.get)
//  protected def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = None
//
//  val wsClient = createClient
//
//  sequential
//
//  def awaiting[T]: Future[MatchResult[T]] => Result = {
//    _.await
//  }
//
//  def createClient = {
//    implicit val system = ActorSystem()
//    implicit val materializer = ActorMaterializer()
//    val wsClient = AhcWSClient()
//    wsClient
//  }
//
//  def withMyDatabase[T](block: Database => T) = {
//    Databases.withDatabase(
//      driver = "org.postgresql.Driver",
//      url = "jdbc:postgresql://localhost/dataplug",
//      name = "dataplug",
//      config = Map(
//        "user" -> "dataplug",
//        "password" -> "secret"))(block)
//  }
//
//  override def afterAll: Unit = {
//    wsClient.close()
//  }
//
//  "Generic Data Plug Endpoint Interface" should {
//
//    "be defined" in {
//      true must beEqualTo(true)
//    }
//  }
//}
