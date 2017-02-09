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
  Library.Play.Db.liquibase,
  Library.HATDeX.hatClient,
  Library.HATDeX.marketsquareClient,
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

javaOptions in Test += "-Dconfig.file=conf/application.test.conf"
