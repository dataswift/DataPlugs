/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 7, 2019
 */

package com.hubofallthings.dataplugUber.models

import play.api.libs.json._

case class UberRide(
    status: String,
    distance: Double,
    product_id: String,
    start_time: Int,
    end_time: Int,
    request_time: Int,
    request_id: String,
    start_city: UberCity)

object UberRide {

  implicit val calendarsReads: Reads[UberRide] = Json.reads[UberRide]
  implicit val calendarsWrites: Writes[UberRide] = Json.writes[UberRide]
}

case class UberCity(
    latitude: Double,
    longitude: Double,
    display_name: String)

object UberCity {

  implicit val calendarsReads: Reads[UberCity] = Json.reads[UberCity]
  implicit val calendarsWrites: Writes[UberCity] = Json.writes[UberCity]
}