package org.hatdex.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class FitbitFat(
  date: String,
  fat: Double,
  logId: Long,
  time: String,
  source: String
)

object FitbitFat {
  implicit val fitbitFatRead: Reads[FitbitFat] = Json.reads[FitbitFat]
}
