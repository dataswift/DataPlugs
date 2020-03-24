/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.utils

import javax.inject.Inject
import akka.stream.Materializer
import com.nimbusds.jose.JWSObject
import com.nimbusds.jwt.JWTClaimsSet
import play.api.http.DefaultHttpFilters
import play.api.mvc._
import play.api.{ Configuration, Environment, Logger }
import play.filters.cors.CORSFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

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

class LoggingFilter @Inject() (configuration: Configuration)(implicit val mat: Materializer, ec: ExecutionContext) extends Filter {
  private val logger = Logger("api")

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {

    val startTime = System.currentTimeMillis

    for {
      result <- nextFilter(requestHeader)
    } yield {
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime

      logger.info(s"[${requestHeader.remoteAddress}] [${requestHeader.method}:${requestHeader.host}:${requestHeader.path}] " +
        s"[${result.header.status}] [$requestTime:ms] ${tokenInfo(requestHeader)} ${processQueryParameters(requestHeader)}")

      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }

  private val authTokenFieldName: String = configuration.getOptional[String]("silhouette.authenticator.fieldName").getOrElse("X-Auth-Token")

  private def processQueryParameters(requestHeader: RequestHeader): String = {
    requestHeader.queryString.toList.map { case (k, v) => s"[$k:${v.mkString("")}]" }.mkString(" ")
  }

  private def tokenInfo(requestHeader: RequestHeader): String = {
    requestHeader.queryString.get("token").flatMap(_.headOption)
      .orElse(requestHeader.headers.get(authTokenFieldName))
      .flatMap(t ⇒ if (t.isEmpty) { None } else { Some(t) })
      .flatMap(t ⇒ Try(JWSObject.parse(t)).toOption)
      .map(o ⇒ JWTClaimsSet.parse(o.getPayload.toJSONObject))
      .map { claimSet =>
        s"[${Option(claimSet.getStringClaim("application")).getOrElse("api")}@" +
          s"${Option(claimSet.getStringClaim("applicationVersion")).getOrElse("_")}]"
      }
      .getOrElse("[unauthenticated@_]")
  }
}
