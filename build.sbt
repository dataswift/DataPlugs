import sbt.Keys._
import sbt._

lazy val dataplug = Project(
  id = "dataplug",
  base = file("dataplug")
)

lazy val dataplugFacebook = Project(
  id = "dataplug-facebook-v2",
  base = file("dataplug-facebook-v2")
).dependsOn(dataplug)

lazy val dataplugTwitter = Project(
  id = "dataplug-twitter-v2",
  base = file("dataplug-twitter-v2")
).dependsOn(dataplug)

lazy val dataplugGoogleCalendar = Project(
  id = "dataplug-google-calendar",
  base = file("dataplug-google-calendar")
).dependsOn(dataplug)

lazy val dataplugMonzo = Project(
  id = "dataplug-monzo",
  base = file("dataplug-monzo")
).dependsOn(dataplug)

lazy val dataplugFitbit = Project(
  id = "dataplug-fitbit",
  base = file("dataplug-fitbit")
).dependsOn(dataplug)

lazy val dataplugSpotify = Project(
  id = "dataplug-spotify",
  base = file("dataplug-spotify")
).dependsOn(dataplug)

val root = Project(
  id = "dataplug-project",
  base = file(".")
).aggregate(dataplug,
            dataplugFacebook,
            dataplugTwitter,
            dataplugGoogleCalendar,
            dataplugMonzo,
            dataplugFitbit,
            dataplugSpotify
).settings(Defaults.coreDefaultSettings ++
  // APIDoc.settings ++
  Seq(
    publishLocal := {},
    publishM2 := {},
    publishArtifact := false
))
