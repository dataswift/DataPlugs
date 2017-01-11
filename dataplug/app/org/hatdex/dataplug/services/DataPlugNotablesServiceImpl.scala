/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplug.services

import javax.inject.Inject

import org.hatdex.dataplug.actors.IoExecutionContext
import org.hatdex.dataplug.apiInterfaces.models.DataPlugSharedNotable
import org.hatdex.dataplug.dao.DataPlugSharedNotableDAO

import scala.concurrent.Future

class DataPlugNotablesServiceImpl @Inject() (dataPlugPostedContentDAO: DataPlugSharedNotableDAO) extends DataPlugNotablesService {
  implicit val ec = IoExecutionContext.ioThreadPool

  /**
   * Finds notable record by given ID.
   *
   * @param notableId Notable ID to be searched for.
   * @return Shared notable metadata record if found.
   */
  def find(notableId: String): Future[Option[DataPlugSharedNotable]] =
    dataPlugPostedContentDAO.find(notableId)

  /**
   * Saves metadata about the shared notable.
   *
   * @param notable Metadata about shared notable.
   * @return Saved version of the metadata about shared notable.
   */
  def save(notable: DataPlugSharedNotable): Future[DataPlugSharedNotable] =
    dataPlugPostedContentDAO.save(notable)
}
