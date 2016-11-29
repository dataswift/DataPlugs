package org.hatdex.dataplugCalendar.models

import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointTableStructure
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class GoogleCalendarEventCreator(
  displayName: Option[String], // The creator's name, if available.
  email: Option[String], // The creator's email address, if available.
  id: Option[String], // The creator's Profile ID, if available. It corresponds to theid field in the People collection of the Google+ API
  self: Option[Boolean] // Whether the creator corresponds to the calendar on which this copy of the event appears. The default is False.
)

case class GoogleCalendarDate(
  date: Option[String], // The date, in the format "yyyy-mm-dd", if this is an all-day event.
  dateTime: Option[String], // The time, as a combined date-time value (formatted according to RFC3339). A time zone offset is required unless a time zone is explicitly specified in timeZone.
  timeZone: Option[String], // The time zone in which the time is specified. (Formatted as an IANA Time Zone Database name, e.g. "Europe/Zurich".) For recurring events this field is required and specifies the time zone in which the recurrence is expanded. For single events this field is optional and indicates a custom time zone for the event start/end.
  endTimeUnspecified: Option[Boolean] // Whether the end time is actually unspecified. An end time is still provided for compatibility reasons, even if this attribute is set to True. The default is False.
)

case class GoogleCalendarGadget(
  display: String, //"The gadget's display mode. Optional. Possible values are: 'icon' - The gadget displays next to the event's title in the calendar view; 'chip' - The gadget displays when the event is clicked."
  iconLink: String, // The gadget's icon URL. The URL scheme must be HTTPS.
  link: String, // The gadget's URL. The URL scheme must be HTTPS.
  title: String, // The gadget's title.
  `type`: String, // The gadget's type.
  height: Option[Int], // The gadget's height in pixels. The height must be an integer greater than 0. Optional.
  width: Option[Int] // The gadget's width in pixels. The width must be an integer greater than 0. Optional.
)

case class GoogleCalendarReminders(
  overrides: Option[String], // If the event doesn't use the default reminders, this lists the reminders specific to the event, or, if not set, indicates that no reminders are set for this event. The maximum number of override reminders is 5. JSON object with 'method'	(The method used by this reminder, "email" - reminders are sent via email, "sms" - reminders are sent via SMS or "popup" - reminders are sent via a UI popup) and "minutes" (Number of minutes before the start of the event when the reminder should trigger)
  useDefault: Option[String] // Whether the default reminders of the calendar apply to the event.
)

case class GoogleCalendarSource(
  title: String, // Title of the source; for example a title of a web page or an email subject.
  url: String // URL of the source pointing to a resource. The URL scheme must be HTTP or HTTPS.
)

case class GoogleCalendarEvent(
    calendarId: Option[String], // Event Calendar ID - added after event is retrieved
    attendees: Option[List[GoogleCalendarAttendee]], // Event attendees
    attendeesOmitted: Option[Boolean], // Whether attendees may have been omitted from the event's representation. When retrieving an event, this may be due to a restriction specified by the maxAttendee query parameter. When updating an event, this can be used to only update the participant's response. Optional. The default is False.
    created: Option[String], // Creation time of the event (as a RFC3339 timestamp). Read-only.
    creator: Option[GoogleCalendarEventCreator], // The creator of the event. Read-only.
    description: Option[String], // Description of the event. Optional.
    end: Option[GoogleCalendarDate], // The (exclusive) end time of the event. For a recurring event, this is the end time of the first instance.
    gadget: Option[GoogleCalendarGadget], // A gadget that extends this event.
    htmlLink: Option[String], // An absolute link to this event in the Google Calendar Web UI.
    iCalUID: Option[String], // Event unique identifier as defined in RFC5545. It is used to uniquely identify events accross calendaring systems and must be supplied when importing events via the import method. Note that the icalUID and the id are not identical and only one of them should be supplied at event creation time. One difference in their semantics is that in recurring events, all occurrences of one event have different ids while they all share the same icalUIDs.
    id: String, //"Opaque identifier of the event."
    location: Option[String], // Geographic location of the event as free-form text.
    organizer: Option[GoogleCalendarEventCreator], // The organizer of the event. If the organizer is also an attendee, this is indicated with a separate entry in attendees with the organizer field set to True.
    originalStartTime: Option[GoogleCalendarDate], // For an instance of a recurring event, this is the time at which this event would start according to the recurrence data in the recurring event identified by recurringEventId. Immutable.
    privateCopy: Option[Boolean], // Whether this is a private event copy where changes are not shared with other copies on other calendars. The default is False.
    recurringEventId: Option[String], // For an instance of a recurring event, this is the id of the recurring event to which this instance belongs.
    reminders: Option[GoogleCalendarReminders], // Information about the event's reminders for the authenticated user.
    source: Option[GoogleCalendarSource], // Source from which the event was created. For example, a web page, an email message or any document identifiable by an URL with HTTP or HTTPS scheme. Can only be seen or modified by the creator of the event.
    start: Option[GoogleCalendarDate], // The (inclusive) start time of the event. For a recurring event, this is the start time of the first instance.
    status: Option[String], //"Status of the event. Optional. Possible values are: 'confirmed' - The event is confirmed. This is the default status; 'tentative' - The event is tentatively confirmed; 'cancelled' - The event is cancelled."
    summary: Option[String], // Title of the event.
    updated: Option[String] // Last modification time of the event (as a RFC3339 timestamp).
) extends ApiEndpointTableStructure {
  val dummyEntity = GoogleCalendarEvent.dummyEntity

  import GoogleCalendarEventJsonProtocol.eventFormat
  def toJson: JsValue = Json.toJson(this)
}

object GoogleCalendarEvent extends ApiEndpointTableStructure {
  val dummyEntity = GoogleCalendarEvent(
    Some("calendarId"),
    None, Some(false),
    Some("created"),
    Some(GoogleCalendarEventCreator(Some("displayName"), Some("email"), Some("id"), Some(false))),
    Some("description"),
    Some(GoogleCalendarDate(Some("date"), Some("dateTime"), Some("timeZone"), Some(false))),
    Some(GoogleCalendarGadget("display", "iconLink", "link", "title", "type", Some(0), Some(0))),
    Some("htmlLink"),
    Some("iCalUID"),
    "id",
    Some("location"),
    Some(GoogleCalendarEventCreator(Some("displayName"), Some("email"), Some("id"), Some(false))),
    Some(GoogleCalendarDate(Some("date"), Some("dateTime"), Some("timeZone"), Some(false))),
    Some(false),
    Some("recurringEventId"),
    Some(GoogleCalendarReminders(Some(""), Some("useDefault"))),
    Some(GoogleCalendarSource("title", "url")),
    Some(GoogleCalendarDate(Some("date"), Some("dateTime"), Some("timeZone"), Some(false))),
    Some("status"),
    Some("summary"),
    Some("updated")
  )

  import GoogleCalendarEventJsonProtocol.eventFormat
  def toJson: JsValue = Json.toJson(dummyEntity)
}

case class GoogleCalendarAttendee(
    eventId: Option[String], // Calendar event ID the attendee is attached to - added after event is retrieved
    calendarId: Option[String], // Calendar ID the attendee is attached to - added after event is retrieved
    additionalGuests: Option[Int], // Number of additional guests. Optional. The default is 0.
    comment: Option[String], // The attendee's response comment. Optional.
    displayName: Option[String], // The attendee's name, if available. Optional.
    email: Option[String], // The attendee's email address, if available. This field must be present when adding an attendee. It must be a valid email address as per RFC5322.
    id: Option[String], // The attendee's Profile ID, if available. It corresponds to the id field in the People collection of the Google+ API
    optional: Option[Boolean], // Whether this is an optional attendee. Optional. The default is False.
    organizer: Option[Boolean], // Whether the attendee is the organizer of the event. Read-only. The default is False.
    resource: Option[Boolean], // Whether the attendee is a resource. Read-only. The default is False.
    responseStatus: Option[String], // "The attendee's response status. Possible values are: 'needsAction' - The attendee has not responded to the invitation; 'declined' - The attendee has declined the invitation; 'tentative' - The attendee has tentatively accepted the invitation; 'accepted' - The attendee has accepted the invitation;"
    self: Option[Boolean] // Whether this entry represents the calendar on which this copy of the event appears. Read-only. The default is False.
) extends ApiEndpointTableStructure {
  val dummyEntity = GoogleCalendarAttendee.dummyEntity

  import GoogleCalendarEventJsonProtocol.attendeeFormat
  def toJson: JsValue = Json.toJson(this)
}

object GoogleCalendarAttendee extends ApiEndpointTableStructure {
  val dummyEntity = GoogleCalendarAttendee(
    Some("eventId"),
    Some("calendarId"),
    Some(0),
    Some("comment"),
    Some("displayName"),
    Some("email"),
    Some("id"),
    Some(false),
    Some(false),
    Some(false),
    Some("responseStatus"),
    Some(false)
  )

  import GoogleCalendarEventJsonProtocol.attendeeFormat
  def toJson: JsValue = Json.toJson(dummyEntity)
}

object GoogleCalendarEventJsonProtocol {

  implicit val eventCreatorFormat = Json.format[GoogleCalendarEventCreator]

  implicit val remindersReads: Reads[GoogleCalendarReminders] = (
    (JsPath \ "overrides").readNullable[List[String]].map(v => v.map(_.toString)) and
    (JsPath \ "useDefaults").readNullable[Boolean].map(v => v.map(_.toString)))(GoogleCalendarReminders.apply _)

  implicit val remindersWrites: Writes[GoogleCalendarReminders] = Json.writes[GoogleCalendarReminders]

  implicit val remindersFormat = Format(remindersReads, remindersWrites)

  implicit val eventDateFormat = Json.format[GoogleCalendarDate]
  implicit val gadgetFormat = Json.format[GoogleCalendarGadget]
  implicit val sourceFormat = Json.format[GoogleCalendarSource]
  implicit val attendeeFormat = Json.format[GoogleCalendarAttendee]
  implicit val eventFormat = Json.format[GoogleCalendarEvent]
}