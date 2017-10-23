package org.hatdex.dataplugFitbit.models

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
