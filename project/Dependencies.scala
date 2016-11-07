/* Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, September 2016
 */

import sbt._

object Dependencies {

  object Versions {
    val crossScala = Seq("2.11.8")
    val scalaVersion = crossScala.head
  }

  val resolvers = Seq(
    "Atlassian Releases" at "https://maven.atlassian.com/public/",
    "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
    "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
  )

  object Library {

    object Play {
      val version = play.core.PlayVersion.current
      val ws = "com.typesafe.play" %% "play-ws" % version
      val cache = "com.typesafe.play" %% "play-cache" % version
      val test = "com.typesafe.play" %% "play-test" % version
      val specs2 = "com.typesafe.play" %% "play-specs2" % version
      val jsonDerivedCodecs = "org.julienrf" % "play-json-derived-codecs_2.11" % "3.3"
      val typesafeConfigExtras = "com.iheart" %% "ficus" % "1.2.6"
      object Specs2 {
        private val version = "3.6.6"
        val matcherExtra = "org.specs2" %% "specs2-matcher-extra" % version
        val mock = "org.specs2" %% "specs2-mock" % version
      }
      object Jwt {
        private val bouncyCastleVersion = "1.55"
        val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleVersion
        val bouncyCastlePkix = "org.bouncycastle" % "bcpkix-jdk15on" % bouncyCastleVersion
        val nimbusDsJwt = "com.nimbusds" % "nimbus-jose-jwt" % "4.22"
      }
      object Db {
        val jdbc = "com.typesafe.play" %% "play-jdbc" % version
        val postgres = "org.postgresql" % "postgresql" % "9.4-1206-jdbc4"
        val anorm = "com.typesafe.play" %% "anorm" % "2.5.2"
      }
    }

    object Specs2 {
      private val version = "3.6.6"
      val core = "org.specs2" %% "specs2-core" % version
      val matcherExtra = "org.specs2" %% "specs2-matcher-extra" % version
      val mock = "org.specs2" %% "specs2-mock" % version
    }

    object Utils {
      val pegdown = "org.pegdown" % "pegdown" % "1.6.0"
    }

    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.0.1"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.4.7"
  }
}
