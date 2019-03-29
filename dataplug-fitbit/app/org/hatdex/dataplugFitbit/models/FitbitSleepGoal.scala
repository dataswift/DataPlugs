package org.hatdex.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class FitbitSleepGoal(
    minDuration: Int,
    updatedOn: String)

object FitbitSleepGoal {
  implicit val fitbitSleepGoalReads: Reads[FitbitSleepGoal] = Json.reads[FitbitSleepGoal]
}
