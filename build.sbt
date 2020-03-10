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
  javaOptions in Universal ++= Seq(
    "-Dhttp.port=9000",
    "-Dlogger.resource=logback.xml",
    "-Dconfig.file=/opt/docker/conf/application.conf"),
  javaOptions in Test += "-Dconfig.file=conf/application.test.conf",
  packageName in Docker := packageName.value,
  maintainer in Docker := maintainer.value,
  version in Docker := version.value,
  dockerExposedPorts := Seq(9000),
  dockerBaseImage := "openjdk:8-jre-alpine",
  dockerEntrypoint := Seq(s"bin/${packageName.value}")
)

lazy val slickCodegenSettings = Seq(
  codegenPackageName in gentables := "com.hubofallthings.dataplug.dal",
  codegenBaseDir in gentables := (baseDirectory.value / "app").getPath,
  codegenClassName in gentables := "Tables",
  codegenExcludedTables in gentables := Seq("databasechangelog", "databasechangeloglock"),
  codegenDatabase in gentables := "devdb",
  codegenConfig in gentables := "reference.conf",
  codegenEvolutions in gentables := "slick.dbs.default.schemaMigrations"
)

val dataplug = project
  .in(file("dataplug"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplug/assets")
  )
  .settings(
    libraryDependencies ++= Seq(
      ehcache,
      filters,
      Library.akkaTestkit,
      Library.HATDeX.codegen,
      Library.HATDeX.dexClient,
      Library.HATDeX.dexter,
      Library.HATDeX.hatClient,
      Library.Play.cache,
      Library.Play.json,
      Library.Play.jsonJoda,
      Library.Play.Jwt.bouncyCastle,
      Library.Play.Jwt.bouncyCastlePkix,
      Library.Play.Jwt.nimbusDsJwt,
      Library.Play.mailer,
      Library.Play.mailerGuice,
      Library.Play.playSlick,
      Library.Play.Silhouette.cryptoJca,
      Library.Play.Silhouette.passwordBcrypt,
      Library.Play.Silhouette.persistence,
      Library.Play.Silhouette.silhouette,
      Library.Play.test,
      Library.Play.typesafeConfigExtras,
      Library.Play.Utils.playBootstrap,
      Library.Play.ws,
      Library.Specs2.core,
      Library.Specs2.matcherExtra,
      Library.Specs2.mock,
      Library.scalaGuice
    )
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .enablePlugins(SlickCodeGeneratorPlugin)
  .settings(slickCodegenSettings)

lazy val dataplugFacebook = Project(id = "dataplug-facebook", base = file("dataplug-facebook"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-facebook",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugFacebook/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugTwitter = Project(id = "dataplug-twitter", base = file("dataplug-twitter"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-twitter",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugTwitter/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugGcalendar = Project(id = "dataplug-gcalendar", base = file("dataplug-gcalendar"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-gcalendar",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugCalendar/assets")
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
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugMonzo/assets")
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
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugFitbit/assets")
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
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugSpotify/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugStarling = Project(id = "dataplug-starling", base = file("dataplug-starling"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-starling",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugStarling/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugInstagram = Project(id = "dataplug-instagram", base = file("dataplug-instagram"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-instagram",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugInstagram/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugUber = Project(id = "dataplug-uber", base = file("dataplug-uber"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-uber",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugUber/assets")
  )
  .enablePlugins(PlayScala, SbtWeb, SbtSassify)
  .settings(buildSettings)
  .enablePlugins(AshScriptPlugin)
  .settings(packageSettings)
  .dependsOn(dataplug)

lazy val dataplugYapily = Project(id = "dataplug-yapily", base = file("dataplug-yapily"))
  .enablePlugins(BasicSettings)
  .settings(
    name := "dataplug-yapily",
    sourceDirectory in Assets := file(s"${baseDirectory.value}/app/com/hubofallthings/dataplugYapily/assets")
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
      dataplugGcalendar,
      dataplugMonzo,
      dataplugFitbit,
      dataplugSpotify,
      dataplugStarling,
      dataplugInstagram,
      dataplugUber,
      dataplugYapily
    )
