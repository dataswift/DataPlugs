/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplug.services

import akka.{ Done, NotUsed }
import akka.stream.scaladsl.Source
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointStatus, ApiEndpointVariant, ApiEndpointVariantChoice }
import org.hatdex.dataplug.models.HatAccessCredentials

import scala.concurrent.Future

/**
 * Handles actions to Users' relationships to Data Plug Endpoints
 */
trait DataPlugEndpointService {

  /**
   * Retrieves a list of all registered endpoints together with corresponding user PHATAs
   *
   * @return The list of tuples of PHATAs and corresponding endpoints
   */
  def retrieveAllEndpoints: Future[Seq[(String, ApiEndpointVariant)]]

  def retrieveAllActiveEndpointsStream: Source[(String, String, ApiEndpointVariant), NotUsed]

  /**
   * Retrieves a user that matches the specified ID.
   *
   * @param phata The user phata.
   * @return The list of retrieved endpoints
   */
  def retrievePhataEndpoints(phata: String): Future[Seq[ApiEndpointVariant]]

  /**
   * Activates an API endpoint for a user
   *
   * @param phata The user phata.
   * @param endpoint The plug endpoint name.
   */
  def activateEndpoint(phata: String, endpoint: String, variant: Option[String], configuration: Option[ApiEndpointCall]): Future[Done]

  /**
   * Deactives API endpoint for a user
   *
   * @param phata The user phata.
   * @param endpoint The plug endpoint name.
   */
  def deactivateEndpoint(phata: String, endpoint: String, variant: Option[String]): Future[Done]

  /**
   * Saves endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param endpoint Endpoint configuration
   */
  def saveEndpointStatus(phata: String, endpoint: ApiEndpointStatus): Future[Unit]

  def saveEndpointStatus(phata: String, variant: ApiEndpointVariant,
    endpoint: ApiEndpointCall, success: Boolean, message: Option[String]): Future[Unit]

  /**
   * Fetches endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @return The available API endpoint configurations
   */
  def listCurrentEndpointStatuses(phata: String): Future[Seq[ApiEndpointStatus]]

  /**
   * Fetches cached endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @return The available API endpoint configurations
   */
  def listCachedCurrentEndpointStatuses(phata: String): Future[Seq[ApiEndpointStatus]]

  /**
   * Retrieves most recent endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   * @return The available API endpoint configuration
   */
  def retrieveLastSuccessfulEndpointVariant(phata: String, plugName: String, variant: Option[String]): Future[Option[ApiEndpointVariant]]

  def enabledApiVariantChoices(phata: String): Future[Seq[ApiEndpointVariantChoice]]

  def updateApiVariantChoices(phata: String, variantChoices: Seq[ApiEndpointVariantChoice]): Future[Option[HatAccessCredentials]]
}
