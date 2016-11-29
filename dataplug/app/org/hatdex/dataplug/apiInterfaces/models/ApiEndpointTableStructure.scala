package org.hatdex.dataplug.apiInterfaces.models

import play.api.libs.json.JsValue

trait ApiEndpointTableStructure {
  val dummyEntity: ApiEndpointTableStructure

  def toJson: JsValue
}
