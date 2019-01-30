package org.hatdex.dataplugFacebook.models

import play.api.libs.json.{ Json, Reads }

case class FacebookPost(
    id: String,
    caption: Option[String],
    created_time: String,
    description: Option[String],
    link: Option[String],
    message: Option[String],
    name: Option[String],
    object_id: Option[String],
    place: Option[FacebookPlace],
    full_picture: Option[String],
    status_type: Option[String],
    `type`: String,
    updated_time: String,
    from: FacebookFrom,
    privacy: FacebookPrivacy)

case class FacebookFrom(
    id: String,
    name: String)

case class FacebookPrivacy(
    value: String,
    description: String,
    friends: String,
    allow: String,
    deny: String)

case class FacebookApplication(
    category: Option[String],
    link: String,
    name: String,
    namespace: Option[String],
    id: String)

object FacebookPost {
  implicit val facebookFromReads: Reads[FacebookFrom] = Json.reads[FacebookFrom]
  implicit val facebookPrivacyReads: Reads[FacebookPrivacy] = Json.reads[FacebookPrivacy]
  implicit val facebookApplicationReads: Reads[FacebookApplication] = Json.reads[FacebookApplication]

  implicit val facebookPostReads: Reads[FacebookPost] = Json.reads[FacebookPost]
}
