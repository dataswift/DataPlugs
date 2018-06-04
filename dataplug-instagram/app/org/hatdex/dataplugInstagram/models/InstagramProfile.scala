package org.hatdex.dataplugInstagram.models

import play.api.libs.json._

case class InstagramProfile(
    id: String,
    username: String,
    full_name: String,
    profile_picture: String,
    bio: String,
    website: String,
    is_business: Boolean,
    counts: InstagramCounts)

case class InstagramCounts(
    media: Int,
    follows: Long,
    followed_by: Long)

object InstagramProfile {
  implicit val instagramCountsReads: Reads[InstagramCounts] = Json.reads[InstagramCounts]

  implicit val instagramProfileReads: Reads[InstagramProfile] = Json.reads[InstagramProfile]
}
