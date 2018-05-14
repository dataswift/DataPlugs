package org.hatdex.dataplugFacebook.models

import play.api.libs.json.{ Json, Reads }

case class FacebookProfilePicture(
    height: Int,
    is_silhouette: Boolean,
    url: String,
    width: Int)

object FacebookProfilePicture {
  implicit val facebookProfilePictureReads: Reads[FacebookProfilePicture] = Json.reads[FacebookProfilePicture]
}
