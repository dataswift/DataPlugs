package org.hatdex.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class FitbitWeight(
  bmi: Option[Double],
  fat: Option[Double],
  date: String,
  logId: Long,
  time: String,
  weight: Double,
  source: String
)

object FitbitWeight {
  implicit val fitbitWeightReads: Reads[FitbitWeight] = Json.reads[FitbitWeight]
}
