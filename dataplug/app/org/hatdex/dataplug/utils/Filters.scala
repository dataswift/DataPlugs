/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.utils

import javax.inject.Inject

import akka.stream.Materializer
import play.api.http.DefaultHttpFilters
import play.api.mvc._
import play.api.{ Environment, Logger }
import play.filters.cors.CORSFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter

import scala.concurrent.{ ExecutionContext, Future }

class Filters @Inject() (
    loggingFilter: LoggingFilter,
    tlsFilter: TLSFilter,
    corsFilter: CORSFilter,
    csrfFilter: CSRFFilter,
    securityHeadersFilter: SecurityHeadersFilter)
  extends DefaultHttpFilters(tlsFilter, corsFilter, csrfFilter, loggingFilter)

class TLSFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext, env: Environment) extends Filter {
  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    if (requestHeader.headers.get("X-Forwarded-Proto").getOrElse("http") != "https" && env.mode == play.api.Mode.Prod) {
      if (requestHeader.method == "GET") {
        Future.successful(Results.MovedPermanently("https://" + requestHeader.host + requestHeader.uri))
      }
      else {
        Future.successful(Results.BadRequest("This service requires strict transport security"))
      }
    }
    else {
      nextFilter(requestHeader).map(_.withHeaders("Strict-Transport-Security" -> "max-age=31536000"))
    }
  }
}

class LoggingFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {
  val logger = Logger("http")

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {

    val startTime = System.currentTimeMillis

    for {
      result <- nextFilter(requestHeader)
    } yield {
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime

      logger.info(s"[${requestHeader.remoteAddress}] [${requestHeader.method}:${requestHeader.host}${requestHeader.uri}] [${result.header.status}] TIME [${requestTime}]ms")

      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }
}
