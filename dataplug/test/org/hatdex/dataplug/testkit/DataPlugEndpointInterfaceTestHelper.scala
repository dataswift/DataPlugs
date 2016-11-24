package org.hatdex.dataplug.testkit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.Future

trait DataPlugEndpointInterfaceTestHelper extends Specification {
  implicit val ee: ExecutionEnv

  def awaiting[T]: Future[MatchResult[T]] => Result = {
    _.await
  }

  def createClient = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val wsClient = AhcWSClient()
    wsClient
  }

  val client = createClient
}
