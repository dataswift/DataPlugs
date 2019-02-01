package org.hatdex.dataplugFacebook.models

import play.api.libs.json.{ Json, Reads }

case class FacebookUserLikes(
    id: String,
    about: String,
    description: Option[String],
    description_html: Option[String],
    fan_count: String,
    has_added_app: Boolean,
    has_whatsapp_number: Boolean,
    link: String,
    name: String,
    phone: Option[String],
    place_type: Option[String],
    username: Option[String],
    website: Option[String],
    created_time: String)

object FacebookUserLikes {
  implicit val facebookUserLikesReads: Reads[FacebookUserLikes] = Json.reads[FacebookUserLikes]
}

