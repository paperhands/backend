package app.paperhands.export

import cats._
import cats.effect._
import cats.implicits._

import app.paperhands.io.Logger

object Export {
  val logger = Logger("export")

  def exportData: IO[ExitCode] =
    IO.unit.as(ExitCode.Success)

  def run(target: Option[String]): IO[ExitCode] =
    target match {
      case Some("content") => exportData
      case _ =>
        logger
          .error(s"Unknown export target '$target'")
          .as(ExitCode.Error)
    }
}
