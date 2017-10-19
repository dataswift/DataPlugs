
enablePlugins(PlayScala)
enablePlugins(SbtWeb, SbtSassify)

routesGenerator := InjectedRoutesGenerator

pipelineStages := Seq(uglify, digest, gzip)
includeFilter in gzip := "*.js || *.css || *.svg || *.png"
sourceDirectory in Assets := baseDirectory.value / "app" / "org" / "hatdex" / "dataplugFitbit" / "assets"

import com.typesafe.sbt.packager.docker._
packageName in Docker := "calendar-dataplug"
maintainer in Docker := "andrius.aucinas@hatdex.org"
version in Docker := version.value
dockerExposedPorts := Seq(9000)
dockerBaseImage := "java:8"

javaOptions in Test += "-Dconfig.file=conf/application.test.conf"
