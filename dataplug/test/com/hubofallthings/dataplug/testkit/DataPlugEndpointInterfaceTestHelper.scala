/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.testkit

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
