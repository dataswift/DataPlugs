package org.hatdex.dataplugFitbit.models

import play.api.libs.json._

case class FitbitProfile(
    dateCreated: Option[String],
    aboutMe: Option[String],
    age: Option[Int],
    avatar: String,
    averageDailySteps: Int,
    city: Option[String],
    clockTimeDisplayFormat: String,
    country: Option[String],
    dateOfBirth: String,
    displayName: String,
    encodedId: String,
    firstName: String,
    fullName: String,
    gender: String,
    height: Double,
    lastName: String,
    locale: String,
    memberSince: String,
    swimUnit: String,
    timezone: String,
    weight: Double)

object FitbitProfile {
  implicit val fitbitProfileReads: Reads[FitbitProfile] = Json.reads[FitbitProfile]
}
