package org.hatdex.dataplugCalendar.models

import play.api.libs.json._

case class GoogleCalendar(
  summary: String, // The calendar's name.
  id: String, // The calendar's ID.
)

object GoogleCalendar{

  implicit val calendarsReads: Reads[GoogleCalendar] = Json.reads[GoogleCalendar]
  implicit val calendarsWrites: Writes[GoogleCalendar] = Json.writes[GoogleCalendar]
}
