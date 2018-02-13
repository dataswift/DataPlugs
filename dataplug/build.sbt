import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.ws,
//  Library.Play.cache,
  Library.Play.test,
  Library.Play.specs2,
  Library.Play.json,
  Library.Play.jsonJoda,
  Library.Play.mailer,
  Library.Play.Specs2.matcherExtra,
  Library.Play.Specs2.mock,
  Library.Play.typesafeConfigExtras,
  Library.Specs2.core,
  Library.Play.Jwt.bouncyCastle,
  Library.Play.Jwt.bouncyCastlePkix,
  Library.Play.Jwt.nimbusDsJwt,
  Library.Play.Silhouette.passwordBcrypt,
  Library.Play.Silhouette.persistence,
  Library.Play.Silhouette.cryptoJca,
  Library.Play.Silhouette.silhouette,
  Library.Play.Utils.playBootstrap,
  Library.akkaTestkit,
  Library.Play.Db.jdbc,
  Library.Play.Db.postgres,
  Library.Play.Db.anorm,
  Library.Utils.pegdown,
  Library.scalaGuice,
  Library.Play.Db.liquibase,
  Library.HATDeX.hatClient,
  Library.HATDeX.dexClient,
  Library.HATDeX.dexter,
  filters
)

enablePlugins(PlayScala)
enablePlugins(SbtWeb)
enablePlugins(SbtSassify)

pipelineStages := Seq(uglify, digest, gzip)
includeFilter in gzip := "*.js || *.css || *.svg || *.png"
sourceDirectory in Assets := baseDirectory.value / "app" / "org" / "hatdex" / "dataplug" / "assets"

routesGenerator := InjectedRoutesGenerator
PlayKeys.devSettings += ("play.http.router", "dataplug.Routes")

import com.typesafe.sbt.packager.docker._
enablePlugins(AshScriptPlugin)
javaOptions in Universal ++= Seq("-Dhttp.port=9000")

packageName in Docker := packageName.value
maintainer in Docker := maintainer.value
version in Docker := version.value
dockerExposedPorts := Seq(9000)
dockerBaseImage := "openjdk:8-jre-alpine"
dockerEntrypoint := Seq(s"bin/${packageName.value}")


javaOptions in Test += "-Dconfig.file=conf/application.test.conf"
