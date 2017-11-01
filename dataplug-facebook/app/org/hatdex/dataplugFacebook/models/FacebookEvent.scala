package org.hatdex.dataplugFacebook.models

import play.api.libs.json.{ Json, Reads }

case class FacebookEvent(
    id: String,
    name: String,
    description: Option[String],
    owner: FacebookEventOwner,
    start_time: String,
    end_time: Option[String],
    updated_time: String,
    attending_count: Int,
    declined_count: Int,
    maybe_count: Int,
    noreply_count: Int,
    rsvp_status: String,
    `type`: String,
    place: Option[FacebookPlace])

case class FacebookEventOwner(
    id: String,
    name: String)

case class FacebookPlace(
    id: Option[String],
    name: String,
    location: Option[FacebookLocation])

case class FacebookLocation(
    city: Option[String],
    country: Option[String],
    latitude: Double,
    longitude: Double,
    located_in: Option[String],
    name: Option[String],
    state: Option[String],
    street: Option[String],
    zip: Option[String])

object FacebookPlace {
  implicit val facebookLocationReads: Reads[FacebookLocation] = Json.reads[FacebookLocation]
  implicit val facebookPlaceReads: Reads[FacebookPlace] = Json.reads[FacebookPlace]
}

object FacebookEvent {
  implicit val facebookEventOwnerReads: Reads[FacebookEventOwner] = Json.reads[FacebookEventOwner]

  implicit val facebookEventReads: Reads[FacebookEvent] = Json.reads[FacebookEvent]
}
