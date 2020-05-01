/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 3, 2017
 */

package com.hubofallthings.dataplugCalendar.apiInterfaces

import com.google.inject.Inject
import com.hubofallthings.dataplug.apiInterfaces.DataPlugOptionsCollector
import com.hubofallthings.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpoint, ApiEndpointCall, ApiEndpointMethod, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.services.UserService
import com.hubofallthings.dataplug.utils.Mailer
import com.hubofallthings.dataplugCalendar.models.GoogleCalendar
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.hubofallthings.dataplugCalendar.apiInterfaces.authProviders._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

class GoogleCalendarList @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val provider: GoogleProvider) extends DataPlugOptionsCollector with RequestAuthenticatorOAuth2 {

  val namespace: String = "calendar"
  val endpoint: String = "google/events"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = ApiEndpointCall(
    "https://www.googleapis.com",
    "/calendar/v3/users/me/calendarList",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("fields" -> "kind,nextSyncToken,items(id,summary)"),
    Map(),
    Some(Map()))

  def generateEndpointChoices(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    staticEndpointChoices ++ generateCalendarEventsEndpoints(maybeResponseBody)
  }

  def staticEndpointChoices: Seq[ApiEndpointVariantChoice] = {
    val calendarsVariant = ApiEndpointVariant(
      ApiEndpoint("google/calendars", "User's google calendars", None),
      Some(""), Some(""),
      Some(GoogleCalendarsInterface.defaultApiEndpoint))

    Seq(
      ApiEndpointVariantChoice("google/calendars", "User's google calendars", active = true, calendarsVariant))
  }

  private def generateCalendarEventsEndpoints(maybeResponseBody: Option[JsValue]): Seq[ApiEndpointVariantChoice] = {
    maybeResponseBody.flatMap { responseBody =>
      (responseBody \ "items").asOpt[Seq[GoogleCalendar]].map { calendars =>
        calendars.map { calendar =>
          val pathParameters = GoogleCalendarEventsInterface.defaultApiEndpoint.pathParameters + ("calendarId" -> calendar.id)
          val variant = ApiEndpointVariant(
            ApiEndpoint("google/events", "Google Calendars", None),
            Some(calendar.id), Some(calendar.summary),
            Some(GoogleCalendarEventsInterface.defaultApiEndpoint.copy(
              pathParameters = pathParameters,
              storageParameters = Some(Map("calendarName" -> calendar.summary)))))

          ApiEndpointVariantChoice(calendar.id, calendar.summary, active = true, variant)
        }
      }
    }.getOrElse(Seq())
  }

}
