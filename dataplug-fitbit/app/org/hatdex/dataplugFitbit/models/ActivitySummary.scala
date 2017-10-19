package org.hatdex.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class FitbitActivityDistance(
  activity: String,
  distance: Double
)

object FitbitActivityDistance {
  implicit val fitbitActivityDistanceReads: Reads[FitbitActivityDistance] = Json.reads[FitbitActivityDistance]
}

case class FitbitActivitySummary(
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
  veryActiveMinutes: Int
)

object FitbitActivitySummary {
  implicit val fitbitActivitySummaryReads: Reads[FitbitActivitySummary] = Json.reads[FitbitActivitySummary]
}
