package org.hatdex.dataplugSpotify.models

import play.api.libs.json.{ Json, Reads }

case class SpotifyProfile(
    birthdate: String,
    country: String,
    dateCreated: String,
    display_name: Option[String],
    email: String,
    href: String,
    id: String,
    product: String,
    `type`: String,
    uri: String)

object SpotifyProfile {
  implicit val spotifyProfileReads: Reads[SpotifyProfile] = Json.reads[SpotifyProfile]
}
