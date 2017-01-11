/*
 * Copyright (C) 2017 HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Augustinas Markevicius <augustinas.markevicius@hatdex.org> 1, 2017
 */

package org.hatdex.dataplugTwitter.models

import play.api.libs.json._

case class TwitterStatusUpdate(
    id_str: String
) {
  implicit val twitterStatusUpdateFormat = TwitterStatusUpdate.twitterStatusUpdateFormat
}

object TwitterStatusUpdate {
  implicit val twitterStatusUpdateFormat = Json.format[TwitterStatusUpdate]
}
