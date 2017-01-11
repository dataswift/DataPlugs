/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplug.apiInterfaces.models

import play.api.libs.json._

case class DataPlugNotableShareRequest(
    message: String,
    hatDomain: String,
    notableId: String
) {
  implicit val notableShareRequestFormat = DataPlugNotableShareRequest.notableShareRequestFormat

  def dataPlugSharedNotable = DataPlugSharedNotable(notableId, hatDomain, posted = false, None, None, deleted = false, None)
}

object DataPlugNotableShareRequest {
  implicit val notableShareRequestFormat = Json.format[DataPlugNotableShareRequest]
}
