package org.hatdex.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class FitbitWeightGoal(
    startDate: String,
    startWeight: Double,
    weight: Double)

object FitbitWeightGoal {
  implicit val fitbitWeightGoalReads: Reads[FitbitWeightGoal] = Json.reads[FitbitWeightGoal]
}