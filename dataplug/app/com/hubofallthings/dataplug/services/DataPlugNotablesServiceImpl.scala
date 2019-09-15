/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplug.services

import com.hubofallthings.dataplug.actors.IoExecutionContext
import com.hubofallthings.dataplug.apiInterfaces.models.DataPlugSharedNotable
import com.hubofallthings.dataplug.dao.DataPlugSharedNotableDAO
import javax.inject.Inject

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
