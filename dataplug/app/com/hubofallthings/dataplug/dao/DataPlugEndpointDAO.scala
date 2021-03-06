/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.dao

import akka.{ Done, NotUsed }
import akka.stream.scaladsl.Source
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointStatus, ApiEndpointVariant }

import scala.concurrent.Future

/**
 * Give access to the user object.
 */
trait DataPlugEndpointDAO {
  /**
   * Retrieves a user that matches the specified ID.
   *
   * @param phata The user phata.
   * @return The list of retrieved endpoints, as a String value
   */
  def retrievePhataEndpoints(phata: String): Future[Seq[ApiEndpointVariant]]

  /**
   * Retrieves a list of all registered endpoints together with corresponding user PHATAs
   *
   * @return The list of tuples of PHATAs and corresponding endpoints
   */
  def retrieveAllEndpoints: Future[Seq[(String, ApiEndpointVariant)]]

  def retrieveAllActiveEndpointsStream: Source[(String, String, ApiEndpointVariant), NotUsed]

  /**
   * Activates an API endpoint for a user
   *
   * @param phata    The user phata.
   * @param endpoint The plug endpoint name.
   * @param variant The plug variant.
   * @param configuration The plug configuration.
   */
  def activateEndpoint(phata: String, endpoint: String, variant: Option[String], configuration: Option[ApiEndpointCall]): Future[Done]

  /**
   * Deactivates API endpoint for a user
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   */
  def deactivateEndpoint(phata: String, plugName: String, variant: Option[String]): Future[Done]

  /**
   * Saves endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param endpoint Endpoint configuration
   */
  def saveEndpointStatus(phata: String, endpoint: ApiEndpointStatus): Future[Done]

  /**
   * Fetches endpoint status from logs for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @return The available API endpoint configurations
   */
  def listEndpointStatusLogs(phata: String): Future[Seq[ApiEndpointStatus]]

  /**
   * Fetches endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @return The available API endpoint configurations
   */
  def listCurrentEndpointStatuses(phata: String): Future[Seq[ApiEndpointStatus]]

  /**
   * Retrieves most recent endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   * @return The available API endpoint configuration
   */
  def retrieveLastSuccessfulEndpointVariant(phata: String, plugName: String, variant: Option[String]): Future[Option[ApiEndpointVariant]]
}
