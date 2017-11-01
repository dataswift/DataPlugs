/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

import sbt.Keys._
import sbt._

object Build extends Build {

  lazy val dataplug = Project(
    id = "dataplug",
    base = file("dataplug")
  )

  lazy val dataplugFacebook = Project(
    id = "dataplug-facebook",
    base = file("dataplug-facebook"),
    dependencies = Seq(
      dataplug % "compile->compile;test->test")
  )

  lazy val dataplugTwitter = Project(
    id = "dataplug-twitter",
    base = file("dataplug-twitter"),
    dependencies = Seq(
      dataplug % "compile->compile;test->test")
  )

  lazy val dataplugGoogleCalendar = Project(
    id = "dataplug-google-calendar",
    base = file("dataplug-google-calendar"),
    dependencies = Seq(
      dataplug % "compile->compile;test->test")
  )

  lazy val dataplugMonzo = Project(
    id = "dataplug-monzo",
    base = file("dataplug-monzo"),
    dependencies = Seq(
      dataplug % "compile->compile;test->test"))

  lazy val dataplugFitbit = Project(
    id = "dataplug-fitbit",
    base = file("dataplug-fitbit"),
    dependencies = Seq(
      dataplug % "compile->compile;test->test"
    )
  )

  val root = Project(
    id = "dataplug-project",
    base = file("."),
    aggregate = Seq(
      dataplug,
      dataplugFacebook,
      dataplugTwitter,
      dataplugGoogleCalendar,
      dataplugMonzo,
      dataplugFitbit
    ),
    settings = Defaults.coreDefaultSettings ++
      // APIDoc.settings ++
      Seq(
        publishLocal := {},
        publishM2 := {},
        publishArtifact := false
      )
  )
}