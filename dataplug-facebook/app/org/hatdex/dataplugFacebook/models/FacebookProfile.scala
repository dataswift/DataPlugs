package org.hatdex.dataplugFacebook.models

import play.api.libs.json._

case class FacebookProfile(
    id: String,
    birthday: Option[String],
    email: Option[String],
    first_name: String,
    last_name: String,
    gender: Option[String], //need extra permissions
    name: String,
    age_range: Option[String],
    link: Option[String])

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
  //  implicit val facebookBasicUserReads: Reads[FacebookBasicUser] = Json.reads[FacebookBasicUser]
  //  implicit val facebookHometownReads: Reads[FacebookHometown] = Json.reads[FacebookHometown]
  //  implicit val facebookSignificantOther: Reads[FacebookSignificantOther] = Json.reads[FacebookSignificantOther]

  implicit val facebookProfileReads: Reads[FacebookProfile] = Json.reads[FacebookProfile]
}
