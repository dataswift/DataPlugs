/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 2, 2017
 */

package org.hatdex.dataplug.dao

import org.hatdex.dataplug.apiInterfaces.models.DataPlugSharedNotable

import scala.concurrent.Future

/**
 * Give access to shared notables
 */
trait DataPlugSharedNotableDAO {
  /**
   * Finds notable record by given ID.
   *
   * @param notableId Notable ID to be searched for.
   * @return Shared notable metadata record if found.
   */
  def find(notableId: String): Future[Option[DataPlugSharedNotable]]

  /**
   * Saves metadata about the shared notable.
   *
   * @param notable Metadata about shared notable.
   * @return Saved version of the metadata about shared notable.
   */
  def save(notable: DataPlugSharedNotable): Future[DataPlugSharedNotable]

}
