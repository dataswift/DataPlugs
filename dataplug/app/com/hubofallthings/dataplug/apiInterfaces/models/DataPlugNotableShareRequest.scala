/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 2, 2017
 */

package com.hubofallthings.dataplug.apiInterfaces.models

import play.api.libs.json._

case class DataPlugNotableShareRequest(
    message: String,
    hatDomain: String,
    notableId: String,
    photo: Option[String]) {
  implicit val notableShareRequestFormat = DataPlugNotableShareRequest.notableShareRequestFormat

  def dataPlugSharedNotable = DataPlugSharedNotable(notableId, hatDomain, posted = false, None, None, deleted = false, None)
}

object DataPlugNotableShareRequest {
  implicit val notableShareRequestFormat = Json.format[DataPlugNotableShareRequest]
}
