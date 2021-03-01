val scala3Version = "2.13.5"
val circeVersion = "0.14.0-M4"
val http4sVersion = "0.21.19"
val sttpVersion = "3.1.6"

lazy val root = project
  .in(file("."))
  .settings(
    name := "app.paperhands",
    version := "0.1.0",

    scalaVersion := scala3Version,
    scalacOptions += "-Ymacro-annotations",
    fork in run := true,
    cancelable in Global := true,
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    testFrameworks += new TestFramework("minitest.runner.Framework"),

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

      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "ch.qos.logback" % "logback-classic" % "1.2.3",

      "org.tpolecat" %% "doobie-core"      % "0.10.0",
      "org.tpolecat" %% "doobie-hikari"    % "0.10.0",
      "org.tpolecat" %% "doobie-postgres"  % "0.10.0",

       "com.github.julien-truffaut" %% "monocle-core"  % "3.0.0-M3",
       "com.github.julien-truffaut" %% "monocle-macro" % "3.0.0-M3", // Not required for Scala 3

       "io.monix" %% "minitest" % "2.9.3" % "test",
       "io.monix" %% "minitest-laws" % "2.9.3" % "test",
       "com.codecommit" %% "cats-effect-testing-minitest" % "0.5.2" % "test",

       "org.flywaydb"  % "flyway-core"     % "7.5.4",
    ),

  )
