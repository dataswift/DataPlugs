/*
 * Copyright (C) 2017-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Augustinas Markevicius <augustinas.markevicius@dataswift.io> 8, 2017
 */

package com.hubofallthings.dataplugFitbit.models

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
