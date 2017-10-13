package org.hatdex.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class ActivityLevel(
  minutes: Int,
  name: String
)

case class HeartRateZone(
  max: Int,
  min: Int,
  minutes: Int,
  name: String
)

case class RecordSource(
  id: String,
  name: String,
  `type`: String,
  url: String
)

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
  steps: Option[Int]
)

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
