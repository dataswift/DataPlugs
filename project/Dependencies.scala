/*
 * Copyright (C) 2016 HAT Data Exchange Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
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
    "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    "HAT Library Artifacts Releases" at "https://s3-eu-west-1.amazonaws.com/library-artifacts-releases.hubofallthings.com",
    "HAT Library Artifacts Snapshots" at "https://s3-eu-west-1.amazonaws.com/library-artifacts-snapshots.hubofallthings.com"
  )


  object Library {
    object HATDeX {
      private val version = "2.4.0-SNAPSHOT"
      val hatClient = "org.hatdex" %% "hat-client-scala-play" % version
      val marketsquareClient = "org.hatdex" %% "marketsquare-client-scala-play" % "2.2.0"
    }

    object Play {
      val version = play.core.PlayVersion.current
      val ws = "com.typesafe.play" %% "play-ws" % version
      val cache = "com.typesafe.play" %% "play-cache" % version
      val test = "com.typesafe.play" %% "play-test" % version
      val specs2 = "com.typesafe.play" %% "play-specs2" % version
      val jsonDerivedCodecs = "org.julienrf" % "play-json-derived-codecs_2.11" % "3.3"
      val typesafeConfigExtras = "com.iheart" %% "ficus" % "1.3.4"
      val mailer = "com.typesafe.play" %% "play-mailer" % "5.0.0"

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
        val liquibase = "org.liquibase" % "liquibase-maven-plugin" % "3.5.1"
      }

      object Utils {
        val playBootstrap = "com.adrianhurt" %% "play-bootstrap" % "1.1-P25-B3" exclude("org.webjars", "jquery")
        val commonsValidator = "commons-validator" % "commons-validator" % "1.5.0"
        val htmlCompressor = "com.mohiva" %% "play-html-compressor" % "0.6.3"
      }

      object Silhouette {
        val passwordBcrypt = "com.mohiva" %% "play-silhouette-password-bcrypt" % "4.0.0"
        val persistence = "com.mohiva" %% "play-silhouette-persistence" % "4.0.0"
        val cryptoJca = "com.mohiva" %% "play-silhouette-crypto-jca" % "4.0.0"
        val silhouette = "com.mohiva" %% "play-silhouette" % "4.0.0"
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
      val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.9"
    }

    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.0.1"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.4.7"
  }
}
