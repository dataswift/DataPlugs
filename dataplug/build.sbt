import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.ws,
//  Library.Play.cache,
  Library.Play.test,
  Library.Play.specs2,
  Library.Play.Specs2.matcherExtra,
  Library.Play.Specs2.mock,
  Library.Play.typesafeConfigExtras,
  Library.Specs2.core,
  Library.Play.Jwt.bouncyCastle,
  Library.Play.Jwt.bouncyCastlePkix,
  Library.Play.Jwt.nimbusDsJwt,
  Library.Play.Utils.playBootstrap,
  Library.akkaTestkit,
  Library.Play.Db.jdbc,
  Library.Play.Db.postgres,
  Library.Play.Db.anorm,
  Library.Utils.pegdown
)

enablePlugins(PlayScala)

libraryDependencies += filters

enablePlugins(SbtWeb)
enablePlugins(SbtSassify)

routesGenerator := InjectedRoutesGenerator

pipelineStages := Seq(uglify, digest, gzip)
includeFilter in gzip := "*.js || *.css || *.svg || *.png"
sourceDirectory in Assets := baseDirectory.value / "app" / "org" / "hatdex" / "dataplug" / "assets"

import com.typesafe.sbt.packager.docker._
packageName in Docker := packageName.value
maintainer in Docker := maintainer.value
version in Docker := version.value
dockerExposedPorts := Seq(9000)
dockerBaseImage := "java:8"

javaOptions in Test += "-Dconfig.file=conf/application.test.conf"
