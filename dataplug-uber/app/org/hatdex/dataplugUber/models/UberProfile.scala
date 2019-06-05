package org.hatdex.dataplugUber.models

import play.api.libs.json.{ Json, Reads, Writes }

case class UberProfile(
    first_name: String,
    last_name: String,
    email: String,
    picture: String,
    mobile_verified: Boolean,
    promo_code: String,
    uuid: String,
    rider_id: String)

object UberProfile {

  implicit val calendarsReads: Reads[UberProfile] = Json.reads[UberProfile]
  implicit val calendarsWrites: Writes[UberProfile] = Json.writes[UberProfile]
}
