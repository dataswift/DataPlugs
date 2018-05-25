package org.hatdex.dataplugStarling.models

import play.api.libs.json.{ Json, Reads }

case class StarlingIndividualProfile(
    firstName: String,
    lastName: String,
    dateOfBirth: String,
    email: String,
    phone: String)

object StarlingIndividualProfile {
  implicit val starlingIndividualProfileReads: Reads[StarlingIndividualProfile] = Json.reads[StarlingIndividualProfile]
}
