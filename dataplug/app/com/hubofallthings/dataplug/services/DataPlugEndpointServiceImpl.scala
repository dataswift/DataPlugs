/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

package com.hubofallthings.dataplug.services

import akka.{ Done, NotUsed }
import akka.stream.scaladsl.Source
import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointStatus, ApiEndpointVariant, ApiEndpointVariantChoice }
import com.hubofallthings.dataplug.dao.DataPlugEndpointDAO
import com.hubofallthings.dataplug.models.HatAccessCredentials
import javax.inject.Inject
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * Handles actions to users.
 *
 */
class DataPlugEndpointServiceImpl @Inject() (
    dataPlugEndpointDao: DataPlugEndpointDAO,
    hatTokenService: HatTokenService) extends DataPlugEndpointService {
  implicit val ec = IoExecutionContext.ioThreadPool

  /**
   * Retrieves a user that matches the specified ID.
   *
   * @param phata The user phata.
   * @return The list of retrieved endpoints, as a String value
   */
  def retrievePhataEndpoints(phata: String): Future[Seq[ApiEndpointVariant]] =
    dataPlugEndpointDao.retrievePhataEndpoints(phata)

  /**
   * Retrieves a list of all registered endpoints together with corresponding user PHATAs
   *
   * @return The list of tuples of PHATAs and corresponding endpoints
   */
  def retrieveAllEndpoints: Future[Seq[(String, ApiEndpointVariant)]] =
    dataPlugEndpointDao.retrieveAllEndpoints

  def retrieveAllActiveEndpointsStream: Source[(String, String, ApiEndpointVariant), NotUsed] =
    dataPlugEndpointDao.retrieveAllActiveEndpointsStream

  /**
   * Activates an API endpoint for a user
   *
   * @param phata The user phata.
   * @param endpoint The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   * @param configuration Initial configuration of the endpoint variant
   */
  def activateEndpoint(phata: String, endpoint: String, variant: Option[String], configuration: Option[ApiEndpointCall]): Future[Done] =
    dataPlugEndpointDao.activateEndpoint(phata, endpoint, variant, configuration)

  /**
   * Deactives API endpoint for a user
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   */
  def deactivateEndpoint(phata: String, plugName: String, variant: Option[String]): Future[Done] =
    dataPlugEndpointDao.deactivateEndpoint(phata, plugName, variant)

  /**
   * Saves endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param endpointStatus The endpoint status object
   */
  def saveEndpointStatus(phata: String, endpointStatus: ApiEndpointStatus): Future[Done] =
    dataPlugEndpointDao.saveEndpointStatus(phata, endpointStatus)

  /**
   * Saves endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param variant Endpoint variant to fetch status for
   */
  def saveEndpointStatus(phata: String, variant: ApiEndpointVariant,
    endpointCall: ApiEndpointCall, success: Boolean, message: Option[String]): Future[Done] = {
    dataPlugEndpointDao.retrievePhataEndpoints(phata)
      .map(_.find(e => e.endpoint.name == variant.endpoint.name && e.variant == variant.variant).get)
      .flatMap { endpoint =>
        val status = ApiEndpointStatus(phata, endpoint, endpointCall, DateTime.now(), success, message)
        dataPlugEndpointDao.saveEndpointStatus(phata, status)
      }
  }

  /**
   * Fetches endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @return The available API endpoint configurations
   */
  def listCurrentEndpointStatuses(phata: String): Future[Seq[ApiEndpointStatus]] =
    dataPlugEndpointDao.listCurrentEndpointStatuses(phata)

  /**
   * Retrieves most recent endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   * @return The available API endpoint configuration
   */
  def retrieveLastSuccessfulEndpointVariant(phata: String, plugName: String, variant: Option[String]): Future[Option[ApiEndpointVariant]] =
    dataPlugEndpointDao.retrieveLastSuccessfulEndpointVariant(phata, plugName, variant)

  def enabledApiVariantChoices(phata: String): Future[Seq[ApiEndpointVariantChoice]] = {
    retrievePhataEndpoints(phata) map { activeEndpoints =>
      activeEndpoints.map { endpointVariant =>
        ApiEndpointVariantChoice(
          endpointVariant.variant.filter(_.trim.nonEmpty).getOrElse(endpointVariant.endpoint.name),
          endpointVariant.variantDescription.getOrElse(endpointVariant.endpoint.description),
          active = true, endpointVariant)
      }
    }
  }

  def updateApiVariantChoices(phata: String, variantChoices: Seq[ApiEndpointVariantChoice]): Future[Option[HatAccessCredentials]] = {
    val variantUpdates = variantChoices map { variantChoice =>
      if (variantChoice.active) {
        activateEndpoint(phata, variantChoice.variant.endpoint.name, variantChoice.variant.variant, variantChoice.variant.configuration)
      }
      else {
        deactivateEndpoint(phata, variantChoice.variant.endpoint.name, variantChoice.variant.variant)
      }
    }
    Future.sequence(variantUpdates).flatMap { _ =>
      hatTokenService.forUser(phata)
    }
  }
}
