package org.hatdex.dataplugFitbit.models

import play.api.libs.json._

case class FitbitProfile(
    dateCreated: Option[String],
    aboutMe: Option[String],
    age: Option[Int],
    avatar: Option[String],
    averageDailySteps: Option[Int],
    city: Option[String],
    clockTimeDisplayFormat: String,
    country: Option[String],
    dateOfBirth: Option[String],
    displayName: Option[String],
    encodedId: Option[String],
    firstName: Option[String],
    fullName: Option[String],
    gender: Option[String],
    height: Option[Double],
    lastName: Option[String],
    locale: Option[String],
    memberSince: Option[String],
    swimUnit: Option[String],
    timezone: Option[String],
    weight: Option[Double])

object FitbitProfile {
  implicit val fitbitProfileReads: Reads[FitbitProfile] = Json.reads[FitbitProfile]
}
