package org.hatdex.dataplugInstagram.models

import play.api.libs.json.{ Json, OFormat }

case class InstagramMedia(
    comments: InstagramCount,
    caption: Option[InstagramCaption],
    likes: InstagramCount,
    link: String,
    user: InstagramUser,
    created_time: String,
    `type`: String,
    filter: String,
    tags: List[String],
    id: String,
    location: Option[InstagramLocation])

case class InstagramCount(count: Long)

case class InstagramCaption(
    created_time: String,
    text: String,
    from: InstagramFrom,
    id: String)

case class InstagramFrom(
    username: String,
    full_name: Option[String],
    `type`: Option[String],
    id: String)

case class InstagramUser(
    username: String,
    profile_picture: String,
    id: String)

case class InstagramLocation(
    latitude: Double,
    longitude: Double,
    id: Option[Long],
    street_address: Option[String],
    name: Option[String])

object InstagramMedia {
  implicit val instagramCountReads: OFormat[InstagramCount] = Json.format[InstagramCount]
  implicit val instagramFromReads: OFormat[InstagramFrom] = Json.format[InstagramFrom]
  implicit val instagramCaptionReads: OFormat[InstagramCaption] = Json.format[InstagramCaption]
  implicit val instagramUserReads: OFormat[InstagramUser] = Json.format[InstagramUser]
  implicit val instagramLocationReads: OFormat[InstagramLocation] = Json.format[InstagramLocation]

  implicit val instagramMediaReads: OFormat[InstagramMedia] = Json.format[InstagramMedia]
}
