/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplug.apiInterfaces.models

import org.joda.time.DateTime

case class DataPlugSharedNotable(
  id: String,
  phata: String,
  posted: Boolean,
  postedTime: Option[DateTime],
  providerId: Option[String],
  deleted: Boolean,
  deletedTime: Option[DateTime]
)
