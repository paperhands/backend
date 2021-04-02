val _scalaVersion = "2.13.5"
val circeVersion = "0.14.0-M4"
val http4sVersion = "0.21.21"
val sttpVersion = "3.2.0"
val catsVersion = "2.5.0"
val catsEffVersion = "2.4.1"
val f2sVersion = "2.5.3"
/* TODO migrate to latest cats-effect */
/* val catsEffVersion = "3.0.0" */
/* val f2sVersion = "3.0.0" */
val f2sDataVersion = "0.10.0"
val doobieVersion = "0.12.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "app.paperhands",
    version := "0.1.0",

    scalaVersion := _scalaVersion,
    scalacOptions += "-Ymacro-annotations",
    fork in run := true,
    cancelable in Global := true,
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    testFrameworks += new TestFramework("minitest.runner.Framework"),
    autoCompilerPlugins := true,

    libraryDependencies ++=Seq(
      "io.circe"  %% "circe-core"     % circeVersion,
      "io.circe"  %% "circe-generic"  % circeVersion,
      "io.circe"  %% "circe-parser"   % circeVersion,
      "io.circe"  %% "circe-literal"  % circeVersion,
      "io.circe"  %% "circe-jawn"     % circeVersion,
      "io.circe"  %% "circe-yaml"     % "0.13.1",

      "com.softwaremill.sttp.client3" %% "http4s-backend" % sttpVersion,
      "com.softwaremill.sttp.client3" %% "core" % sttpVersion,

      "org.http4s" %% "http4s-core" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,

      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.3",
      "ch.qos.logback" % "logback-classic" % "1.2.3",

      "org.tpolecat" %% "doobie-core"     % doobieVersion,
      "org.tpolecat" %% "doobie-hikari"   % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,

       "com.github.julien-truffaut" %% "monocle-core"  % "3.0.0-M3",
       "com.github.julien-truffaut" %% "monocle-macro" % "3.0.0-M3", // Not required for Scala 3

       "io.monix" %% "minitest" % "2.9.3" % "test",
       "io.monix" %% "minitest-laws" % "2.9.3" % "test",
       "com.codecommit" %% "cats-effect-testing-minitest" % "0.5.2" % "test",

       "org.flywaydb"  % "flyway-core"     % "7.7.1",

       "net.ruippeixotog" %% "scala-scraper" % "2.2.0",

       "co.fs2" %% "fs2-core" % f2sVersion,
       "co.fs2" %% "fs2-io" % f2sVersion,

       "org.gnieh" %% "fs2-data-csv" % f2sDataVersion,

       "org.typelevel" %% "cats-core" % catsVersion,
       "org.typelevel" %% "cats-effect" % catsEffVersion,

       "me.xdrop" % "fuzzywuzzy" % "1.3.1",
    ),
  )
