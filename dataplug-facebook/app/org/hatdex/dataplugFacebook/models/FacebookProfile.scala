package org.hatdex.dataplugFacebook.models

import play.api.libs.json._

case class FacebookProfile(
    id: String,
    // birthday: Option[String],
    email: String,
    first_name: String,
    last_name: String,
    gender: String,
    is_verified: Boolean,
    locale: String,
    name: String,
    // political: String,
    // relationship_status: String,
    // religion: String,
    // quotes: String,
    third_party_id: String,
    timezone: Int,
    updated_time: String,
    verified: Boolean)
// website: String,
// hometown: FacebookHometown,
// significant_other: FacebookSignificantOther)

case class FacebookHometown(
    id: String,
    name: String)

case class FacebookSignificantOther(
    id: String,
    name: String)

object FacebookProfile {
  implicit val facebookHometownReads: Reads[FacebookHometown] = Json.reads[FacebookHometown]
  implicit val facebookSignificantOther: Reads[FacebookSignificantOther] = Json.reads[FacebookSignificantOther]

  implicit val facebookProfileReads: Reads[FacebookProfile] = Json.reads[FacebookProfile]
}
