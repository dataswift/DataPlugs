package org.hatdex.dataplugFacebook.models

import play.api.libs.json._

case class FacebookProfile(
    id: String,
    birthday: Option[String],
    email: Option[String],
    first_name: String,
    last_name: String,
    friends: Option[List[FacebookBasicUser]],
    // friend_count: Int, the representation changed to summary.total_count how do I update it here?
    // gender: Option[String], need extra permissions
    name: String,
    age_range: Option[String],
    // political: String,
    // relationship_status: String,
    // religion: String,
    // quotes: String,
    link: Option[String])

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
