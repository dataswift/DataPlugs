package org.hatdex.dataplugFitbit.models

import org.hatdex.dataplug.apiInterfaces.models.ApiEndpointTableStructure
import org.hatdex.hat.api.json.DateTimeMarshalling
import org.joda.time.DateTime
import play.api.libs.json._

case class FitbitActivity(
    activityId: Int,
    activityParentId: Int,
    calories: Int,
    description: String,
    distance: Double,
    duration: Int,
    hasStartTime: Boolean,
    isFavorite: Boolean,
    logId: Int,
    name: String,
    startTime: String,
    steps: Int
) extends ApiEndpointTableStructure {
  val dummyEntity = FitbitActivity.dummyEntity

  implicit val fitbitActivityFormat = FitbitActivity.fitbitActivityFormat

  def toJson: JsValue = Json.toJson(this)
}

object FitbitActivity extends ApiEndpointTableStructure {
  val dummyEntity = FitbitActivity(
    51007,
    90019,
    230,
    "7mph",
    2.04,
    1097053,
    true,
    true,
    1154701,
    "Treadmill, 0% Incline",
    "00:25",
    3783
  )

  implicit val fitbitActivityFormat = Json.format[FitbitActivity]

  def toJson: JsValue = Json.toJson(dummyEntity)
}

case class FitbitGoals(
  caloriesOut: Option[Int],
  distance: Option[Double],
  floors: Option[Int],
  steps: Option[Int]
)

object FitbitGoals {
  val dummyValue = FitbitGoals(
    Some(2826),
    Some(8.05),
    Some(150),
    Some(10000)
  )
}

case class FitbitDistance(
  activity: String,
  distance: Double
)

object FitbitDistance {
  val dummyValue = FitbitDistance(
    "tracker",
    1.32
  )
}

case class FitbitDaySummary(
  activityCalories: Int,
  caloriesBMR: Int,
  caloriesOut: Int,
  distances: Option[List[FitbitDistance]],
  elevation: Double,
  fairlyActiveMinutes: Int,
  floors: Int,
  lightlyActiveMinutes: Int,
  marginalCalories: Int,
  sedentaryMinutes: Int,
  steps: Int,
  veryActiveMinutes: Int
)

object FitbitDaySummary {
  val dummyEntity = FitbitDaySummary(
    230,
    1913,
    2143,
    Some(List(FitbitDistance.dummyValue)),
    48.77,
    0,
    16,
    0,
    200,
    1166,
    0,
    0
  )
}

case class FitbitDayActivitySummary(
    activities: Option[List[FitbitActivity]],
    goals: Option[FitbitGoals],
    summary: FitbitDaySummary
) extends ApiEndpointTableStructure {
  val dummyEntity = FitbitDayActivitySummary.dummyEntity

  implicit val fitbitDayActivitySummary = FitbitDayActivitySummary.fitbitDayActivitySummaryFormat

  def toJson: JsValue = Json.toJson(this)
}

object FitbitDayActivitySummary extends ApiEndpointTableStructure {
  val dummyEntity = FitbitDayActivitySummary(
    Some(List(FitbitActivity.dummyEntity)),
    Some(FitbitGoals.dummyValue),
    FitbitDaySummary.dummyEntity
  )

  implicit val fitbitGoalsFormat = Json.format[FitbitGoals]
  implicit val fitbitDistanceFormat = Json.format[FitbitDistance]
  implicit val fitbitDaySummaryFormat = Json.format[FitbitDaySummary]

  implicit val fitbitDayActivitySummaryFormat = Json.format[FitbitDayActivitySummary]

  def toJson: JsValue = Json.toJson(dummyEntity)
}
