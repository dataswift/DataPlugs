/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 3, 2017
 */

package com.hubofallthings.dataplugCalendar.models

import play.api.libs.json._

case class GoogleCalendar(
  summary: String, // The calendar's name.
  id: String, // The calendar's ID.
)

object GoogleCalendar{

  implicit val calendarsReads: Reads[GoogleCalendar] = Json.reads[GoogleCalendar]
  implicit val calendarsWrites: Writes[GoogleCalendar] = Json.writes[GoogleCalendar]
}
