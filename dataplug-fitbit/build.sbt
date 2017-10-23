
enablePlugins(PlayScala)
enablePlugins(SbtWeb, SbtSassify)

routesGenerator := InjectedRoutesGenerator

pipelineStages := Seq(uglify, digest, gzip)
includeFilter in gzip := "*.js || *.css || *.svg || *.png"
sourceDirectory in Assets := baseDirectory.value / "app" / "org" / "hatdex" / "dataplugFitbit" / "assets"

import com.typesafe.sbt.packager.docker._
enablePlugins(AshScriptPlugin)
javaOptions in Universal ++= Seq("-Dhttp.port=9000")

packageName in Docker := "calendar-dataplug"
maintainer in Docker := maintainer.value
version in Docker := version.value
dockerExposedPorts := Seq(9000)
dockerBaseImage := "openjdk:8-jre-alpine"
dockerEntrypoint := Seq(s"bin/${packageName.value}")


javaOptions in Test += "-Dconfig.file=conf/application.test.conf"
