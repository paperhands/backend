val scala3Version = "2.13.5"
val circeVersion = "0.14.0-M4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "app.paperhands",
    version := "0.1.0",

    scalaVersion := scala3Version,

    libraryDependencies ++=Seq(
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "com.softwaremill.sttp.client3" %% "core" % "3.1.6",
      "io.circe"  %% "circe-core"     % circeVersion,
      "io.circe"  %% "circe-generic"  % circeVersion,
      "io.circe"  %% "circe-parser"   % circeVersion,
      "io.circe"  %% "circe-jawn"     % circeVersion,
      "io.circe"  %% "circe-yaml"     % "0.13.1"
    )
  )
