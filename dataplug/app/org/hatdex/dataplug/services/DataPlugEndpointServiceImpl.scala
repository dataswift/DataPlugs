package org.hatdex.dataplug.services

import javax.inject.Inject

import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointStatus, ApiEndpointVariant, ApiEndpointVariantChoice }
import org.hatdex.dataplug.dao.DataPlugEndpointDAO
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * Handles actions to users.
 *
 * @param userDAO The user DAO implementation.
 */
class DataPlugEndpointServiceImpl @Inject() (dataPlugEndpointDao: DataPlugEndpointDAO) extends DataPlugEndpointService {
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

  /**
   * Activates an API endpoint for a user
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   * @param configuration Initial configuration of the endpoint variant
   */
  def activateEndpoint(phata: String, endpoint: String, variant: Option[String], configuration: Option[ApiEndpointCall]): Future[Unit] =
    dataPlugEndpointDao.activateEndpoint(phata, endpoint, variant, configuration)

  /**
   * Deactives API endpoint for a user
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   */
  def deactivateEndpoint(phata: String, plugName: String, variant: Option[String]): Future[Unit] =
    dataPlugEndpointDao.deactivateEndpoint(phata, plugName, variant)

  /**
   * Saves endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param endpointStatus The endpoint status object
   */
  def saveEndpointStatus(phata: String, endpointStatus: ApiEndpointStatus): Future[Unit] =
    dataPlugEndpointDao.saveEndpointStatus(phata, endpointStatus)

  /**
   * Saves endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   */
  def saveEndpointStatus(phata: String, variant: ApiEndpointVariant,
    endpointCall: ApiEndpointCall, success: Boolean, message: Option[String]): Future[Unit] = {
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
   * Saves endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   * @return The available API endpoint configuration
   */
  def retrieveCurrentEndpointStatus(phata: String, plugName: String, variant: Option[String]): Future[Option[ApiEndpointStatus]] =
    dataPlugEndpointDao.retrieveCurrentEndpointStatus(phata, plugName, variant)

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
          endpointVariant.variant.getOrElse(endpointVariant.endpoint.name),
          endpointVariant.variantDescription.getOrElse(endpointVariant.endpoint.description),
          active = true, endpointVariant)
      }
    }
  }

  def updateApiVariantChoices(phata: String, variantChoices: Seq[ApiEndpointVariantChoice]): Future[Unit] = {
    val variantUpdates = variantChoices map { variantChoice =>
      if (variantChoice.active) {
        activateEndpoint(phata, variantChoice.variant.endpoint.name, variantChoice.variant.variant, variantChoice.variant.configuration)
      }
      else {
        deactivateEndpoint(phata, variantChoice.variant.endpoint.name, variantChoice.variant.variant)
      }
    }
    Future.sequence(variantUpdates).map(_ => ())
  }
}