package org.hatdex.dataplugFitbit.models

import play.api.libs.json.{Json, Reads}

case class FitbitFatGoal(
  fat: String)

object FitbitFatGoal {
  implicit val fitbitWeightGoalReads: Reads[FitbitFatGoal] = Json.reads[FitbitFatGoal]
}
