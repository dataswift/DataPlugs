package org.hatdex.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class FitbitBestDetail(
    date: String,
    value: Double)

object FitbitBestDetail {
  implicit val fitbitBestDetailReads: Reads[FitbitBestDetail] = Json.reads[FitbitBestDetail]
}

case class FitbitLifetimeDetail(
    distance: Double,
    steps: Long)

object FitbitLifetimeDetail {
  implicit val fitbitLifetimeDetailReads: Reads[FitbitLifetimeDetail] = Json.reads[FitbitLifetimeDetail]
}

case class FitbitBestDetailGroup(
    distance: FitbitBestDetail,
    steps: FitbitBestDetail)

object FitbitBestDetailGroup {
  implicit val fitbitBestDetailGroupReads: Reads[FitbitBestDetailGroup] = Json.reads[FitbitBestDetailGroup]
}

case class FitbitBestStats(
    total: FitbitBestDetailGroup,
    tracker: FitbitBestDetailGroup)

object FitbitBestStats {
  implicit val fitbitBestStatsReads: Reads[FitbitBestStats] = Json.reads[FitbitBestStats]
}

case class FitbitLifetimeStats(
    total: FitbitLifetimeDetail,
    tracker: FitbitLifetimeDetail)

object FitbitLifetimeStats {
  implicit val fitbitLifetimeStatsReads: Reads[FitbitLifetimeStats] = Json.reads[FitbitLifetimeStats]
}

case class FitbitLifetime(
    best: FitbitBestStats,
    lifetime: FitbitLifetimeStats)

object FitbitLifetime {
  implicit val fitbitLifetimeReads: Reads[FitbitLifetime] = Json.reads[FitbitLifetime]
}
