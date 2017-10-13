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
  Library.Utils.pegdown,
  filters
)

enablePlugins(PlayScala)
enablePlugins(SbtWeb, SbtSassify)

routesGenerator := InjectedRoutesGenerator

pipelineStages := Seq(uglify, digest, gzip)
includeFilter in gzip := "*.js || *.css || *.svg || *.png"
sourceDirectory in Assets := baseDirectory.value / "app" / "org" / "hatdex" / "dataplugSocial" / "assets"

import com.typesafe.sbt.packager.docker._
packageName in Docker := "social-dataplug"
maintainer in Docker := "andrius.aucinas@hatdex.org"
version in Docker := version.value
dockerExposedPorts := Seq(9000)
dockerBaseImage := "java:8"

javaOptions in Test += "-Dconfig.file=conf/application.test.conf"
