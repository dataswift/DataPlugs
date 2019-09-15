/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 8, 2017
 */

package com.hubofallthings.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class FitbitActivityDistance(
    activity: String,
    distance: Double)

object FitbitActivityDistance {
  implicit val fitbitActivityDistanceReads: Reads[FitbitActivityDistance] = Json.reads[FitbitActivityDistance]
}

case class FitbitActivitySummary(
    dateCreated: String,
    activityCalories: Int,
    caloriesBMR: Int,
    caloriesOut: Int,
    distances: List[FitbitActivityDistance],
    elevation: Option[Double],
    fairlyActiveMinutes: Int,
    floors: Option[Int],
    lightlyActiveMinutes: Int,
    marginalCalories: Int,
    sedentaryMinutes: Int,
    steps: Int,
    veryActiveMinutes: Int)

object FitbitActivitySummary {
  implicit val fitbitActivitySummaryReads: Reads[FitbitActivitySummary] = Json.reads[FitbitActivitySummary]
}
