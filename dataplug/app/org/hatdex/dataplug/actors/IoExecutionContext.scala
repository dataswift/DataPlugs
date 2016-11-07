/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.bulletin.actors

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

object IoExecutionContext {
  private val concurrency = Runtime.getRuntime.availableProcessors()
  private val factor = 5 // get from configuration  file
  private val noOfThread = concurrency * factor
  implicit val ioThreadPool: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(noOfThread))
}
