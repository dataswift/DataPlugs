package org.hatdex.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class FitbitActivityGoal(
    caloriesOut: Int,
    distance: Double,
    floors: Option[Int],
    steps: Int)

object FitbitActivityGoal {
  implicit val fitbitActivityGoalReads: Reads[FitbitActivityGoal] = Json.reads[FitbitActivityGoal]
}

