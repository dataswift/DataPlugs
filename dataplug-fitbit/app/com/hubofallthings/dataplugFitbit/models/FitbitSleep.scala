/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 3, 2019
 */

package com.hubofallthings.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class LevelsData(
    dateTime: String,
    level: String,
    seconds: Int)

case class MinuteCount(
    count: Int,
    minutes: Int)

case class LevelsSummary(
    asleep: Option[MinuteCount],
    awake: Option[MinuteCount],
    restless: Option[MinuteCount],
    deep: Option[MinuteCount],
    light: Option[MinuteCount],
    rem: Option[MinuteCount],
    wake: Option[MinuteCount])

case class Levels(
    data: List[LevelsData],
    summary: LevelsSummary)

case class FitbitSleep(
    dateOfSleep: String,
    duration: Int,
    efficiency: Int,
    endTime: String,
    infoCode: Int,
    levels: Levels,
    logId: Long,
    minutesAfterWakeup: Int,
    minutesAsleep: Int,
    minutesAwake: Int,
    minutesToFallAsleep: Int,
    startTime: String,
    timeInBed: Int,
    `type`: String)

object FitbitSleep {
  implicit val levelDataReads: Reads[LevelsData] = Json.reads[LevelsData]
  implicit val minuteCountReads: Reads[MinuteCount] = Json.reads[MinuteCount]
  implicit val levelsSummaryReads: Reads[LevelsSummary] = Json.reads[LevelsSummary]
  implicit val levelsReads: Reads[Levels] = Json.reads[Levels]

  implicit val fitbitSleepReads: Reads[FitbitSleep] = Json.reads[FitbitSleep]
}
