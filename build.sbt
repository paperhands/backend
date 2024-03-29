val _scalaVersion = "2.13.5"
val circeVersion = "0.15.0-M1"
val http4sVersion = "1.0.0-M21"
val catsVersion = "2.8.0"
val catsEffVersion = "3.3.14"
val f2sVersion = "3.2.11"
val f2sDataVersion = "1.4.1"
val doobieVersion = "1.0.0-RC2"
val monocleVersion = "3.0.0-M6"
val minitestVersion = "2.9.6"

inThisBuild(
  List(
    scalaVersion := _scalaVersion,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "app.paperhands",
    version := "0.1.0",

    scalacOptions ++= List(
      "-Yrangepos",
      "-Wunused:imports"
    ),

    scalaVersion := _scalaVersion,
    scalacOptions += "-Ymacro-annotations",
    run / fork := true,
    Global / cancelable := true,
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    testFrameworks += new TestFramework("minitest.runner.Framework"),
    autoCompilerPlugins := true,

    libraryDependencies ++=Seq(
      "io.circe"  %% "circe-core"     % circeVersion,
      "io.circe"  %% "circe-generic"  % circeVersion,
      "io.circe"  %% "circe-parser"   % circeVersion,
      "io.circe"  %% "circe-literal"  % circeVersion,
      "io.circe"  %% "circe-jawn"     % circeVersion,
      "io.circe"  %% "circe-yaml"     % "0.14.1",

      "org.http4s" %% "http4s-core"         % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
      "org.http4s" %% "http4s-circe"        % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,

      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "ch.qos.logback" % "logback-classic"            % "1.2.11",

      "org.tpolecat" %% "doobie-core"     % doobieVersion,
      "org.tpolecat" %% "doobie-hikari"   % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,

       "com.github.julien-truffaut" %% "monocle-core"  % monocleVersion,
       "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion, // Not required for Scala 3

       "io.monix" %% "minitest"      % minitestVersion % "test",
       "io.monix" %% "minitest-laws" % minitestVersion % "test",
       "org.typelevel" %% "cats-effect-testing-minitest" % "1.4.0" % "test",

       "org.flywaydb"  % "flyway-core" % "9.1.2",

       "net.ruippeixotog" %% "scala-scraper" % "3.0.0",

       "co.fs2" %% "fs2-core" % f2sVersion,
       "co.fs2" %% "fs2-io"   % f2sVersion,

       "org.gnieh" %% "fs2-data-csv" % f2sDataVersion,

       "org.typelevel" %% "cats-core"   % catsVersion,
       "org.typelevel" %% "cats-effect" % catsEffVersion,

       "me.xdrop" % "fuzzywuzzy" % "1.4.0",
    ),
  )

addCommandAlias("organizeImports", "scalafix dependency:OrganizeImports@com.github.liancheng:organize-imports:0.5.0")
addCommandAlias("cleanImports", "scalafix RemoveUnused")
