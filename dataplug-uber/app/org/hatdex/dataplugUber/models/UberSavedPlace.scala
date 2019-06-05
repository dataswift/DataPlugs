package org.hatdex.dataplugUber.models

import play.api.libs.json.Json

case class UberSavedPlace(
    address: String)

object UberSavedPlace {
  implicit val addressFormat = Json.format[UberSavedPlace]
}