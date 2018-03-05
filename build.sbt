import Dependencies.Library
import com.typesafe.sbt.SbtNativePackager.autoImport.packageName
import sbt._

lazy val buildSettings = Seq(
  pipelineStages := Seq(uglify, digest, gzip),
  includeFilter in gzip := "*.js || *.css || *.svg || *.png",
  routesGenerator := InjectedRoutesGenerator,
  PlayKeys.devSettings += ("play.http.router", "dataplug.Routes")
)

lazy val packageSettings = Seq(
  javaOptions in Universal ++= Seq("-Dhttp.port=9000"),
  javaOptions in Test += "-Dconfig.file=conf/application.test.conf",
  packageName in Docker := packageName.value,
  maintainer in Docker := maintainer.value,
  version in Docker := version.value,
  dockerExposedPorts := Seq(9000),
  dockerBaseImage := "openjdk:8-jre-alpine",
  dockerEntrypoint := Seq(s"bin/${packageName.value}")
)

val dataplug = project
  .in(file("dataplug"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/org/hatdex/dataplug/assets")
  )
  .settings(
    libraryDependencies ++= Seq(
      ehcache,
      filters,
      Library.akkaTestkit,
      Library.HATDeX.dexClient,
      Library.HATDeX.dexter,
      Library.HATDeX.hatClient,
      Library.Play.cache,
      Library.Play.Db.anorm,
      Library.Play.Db.jdbc,
      Library.Play.Db.liquibase,
      Library.Play.Db.postgres,
      Library.Play.json,
      Library.Play.jsonJoda,
      Library.Play.Jwt.bouncyCastle,
      Library.Play.Jwt.bouncyCastlePkix,
      Library.Play.Jwt.nimbusDsJwt,
      Library.Play.mailer,
      Library.Play.mailerGuice,
      Library.Play.Silhouette.passwordBcrypt,
      Library.Play.Silhouette.persistence,
      Library.Play.Silhouette.cryptoJca,
      Library.Play.Silhouette.silhouette,
      Library.Play.specs2,
      Library.Play.Specs2.matcherExtra,
      Library.Play.Specs2.mock,
      Library.Play.test,
      Library.Play.typesafeConfigExtras,
      Library.Play.ws,
      Library.Play.Utils.playBootstrap,
      Library.scalaGuice,
      Library.Specs2.core,
      Library.Utils.pegdown
    )
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)

lazy val dataplugFacebook = Project(id = "dataplug-facebook-v2", base = file("dataplug-facebook-v2"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-facebook-v2",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/org/hatdex/dataplugFacebook/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugTwitter = Project(id = "dataplug-twitter-v2", base = file("dataplug-twitter-v2"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-twitter-v2",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/org/hatdex/dataplugTwitter/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugGoogleCalendar = Project(id = "dataplug-google-calendar", base = file("dataplug-google-calendar"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-google-calendar",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/org/hatdex/dataplugCalendar/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugMonzo = Project(id = "dataplug-monzo", base = file("dataplug-monzo"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-monzo",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/org/hatdex/dataplugMonzo/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugFitbit = Project(id = "dataplug-fitbit", base = file("dataplug-fitbit"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-fitbit",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/org/hatdex/dataplugFitbit/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugSpotify = Project(id = "dataplug-spotify", base = file("dataplug-spotify"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-spotify",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/org/hatdex/dataplugSpotify/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val root = project
    .in(file("."))
    .settings(
      name := "dataplug-project",
      publishLocal := {},
      publishM2 := {},
      publishArtifact := false,
      skip in publish := true
    )
    .aggregate(
      dataplugFacebook,
      dataplugTwitter,
      dataplugGoogleCalendar,
      dataplugMonzo,
      dataplugFitbit,
      dataplugSpotify
    )
