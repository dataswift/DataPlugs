package org.hatdex.dataplugFacebook.models

import play.api.libs.json._

case class FacebookProfile(
    id: String,
    // birthday: Option[String],
    email: Option[String],
    first_name: String,
    last_name: String,
    friends: Option[List[FacebookBasicUser]],
    friend_count: Int,
    gender: Option[String],
    is_verified: Boolean,
    locale: String,
    name: String,
    // political: String,
    // relationship_status: String,
    // religion: String,
    // quotes: String,
    third_party_id: String,
    timezone: Double,
    updated_time: String,
    verified: Boolean,
    link: Option[String],
    website: Option[String])

// hometown: FacebookHometown,
// significant_other: FacebookSignificantOther)

case class FacebookBasicUser(
    name: String,
    id: String)

case class FacebookHometown(
    id: String,
    name: String)

case class FacebookSignificantOther(
    id: String,
    name: String)

object FacebookProfile {
  implicit val facebookBasicUserReads: Reads[FacebookBasicUser] = Json.reads[FacebookBasicUser]
  implicit val facebookHometownReads: Reads[FacebookHometown] = Json.reads[FacebookHometown]
  implicit val facebookSignificantOther: Reads[FacebookSignificantOther] = Json.reads[FacebookSignificantOther]

  implicit val facebookProfileReads: Reads[FacebookProfile] = Json.reads[FacebookProfile]
}
