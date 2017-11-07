package org.hatdex.dataplugFacebook.models

import play.api.libs.json.{ Json, Reads }

case class FacebookFeedUpdate(
    id: String)

object FacebookFeedUpdate {
  implicit val facebookFeedUpdateReads: Reads[FacebookFeedUpdate] = Json.reads[FacebookFeedUpdate]
}
