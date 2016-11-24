package org.hatdex.dataplug.services

import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointStatus, ApiEndpointVariant, ApiEndpointVariantChoice }

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
  def activateEndpoint(phata: String, endpoint: String, variant: Option[String], configuration: Option[ApiEndpointCall]): Future[Unit]

  /**
   * Deactives API endpoint for a user
   *
   * @param phata The user phata.
   * @param endpoint The plug endpoint name.
   */
  def deactivateEndpoint(phata: String, endpoint: String, variant: Option[String]): Future[Unit]

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
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   * @return The available API endpoint configuration
   */
  def retrieveCurrentEndpointStatus(phata: String, plugName: String, variant: Option[String]): Future[Option[ApiEndpointStatus]]

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

  def updateApiVariantChoices(phata: String, variantChoices: Seq[ApiEndpointVariantChoice]): Future[Unit]
}
