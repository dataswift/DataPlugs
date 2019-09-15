/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 8, 2017
 */

package com.hubofallthings.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class ActivityLevel(
    minutes: Int,
    name: String)

case class HeartRateZone(
    max: Int,
    min: Int,
    minutes: Int,
    name: String)

case class RecordSource(
    id: String,
    name: String,
    `type`: String,
    url: String)

case class FitbitActivity(
    activeDuration: Int,
    activityLevel: List[ActivityLevel],
    activityName: String,
    averageHeartRate: Option[Int],
    calories: Int,
    distance: Option[Double],
    distanceUnit: Option[String],
    duration: Int,
    elevationGain: Option[Double],
    heartRateZones: Option[List[HeartRateZone]],
    lastModified: String,
    logId: Long,
    logType: String,
    originalDuration: Int,
    originalStartTime: String,
    pace: Option[Double],
    source: Option[RecordSource],
    speed: Option[Double],
    startTime: String,
    steps: Option[Int])

object ActivityLevel {
  implicit val activityLevelReads: Reads[ActivityLevel] = Json.reads[ActivityLevel]
}

object HeartRateZone {
  implicit val heartRateZoneReads: Reads[HeartRateZone] = Json.reads[HeartRateZone]
}

object RecordSource {
  implicit val recordSourceReads: Reads[RecordSource] = Json.reads[RecordSource]
}

object FitbitActivity {
  implicit val fitbitActivityReads: Reads[FitbitActivity] = Json.reads[FitbitActivity]
}
