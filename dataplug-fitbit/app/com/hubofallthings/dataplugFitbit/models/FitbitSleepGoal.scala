/*
 * Copyright (C) 2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Marios Tsekis <marios.tsekis@dataswift.io> 3, 2019
 */

package com.hubofallthings.dataplugFitbit.models

import play.api.libs.json.{ Json, Reads }

case class FitbitSleepGoal(
    minDuration: Int,
    updatedOn: String)

object FitbitSleepGoal {
  implicit val fitbitSleepGoalReads: Reads[FitbitSleepGoal] = Json.reads[FitbitSleepGoal]
}
