package app.paperhands.export

import cats._
import cats.effect._
import cats.implicits._

import app.paperhands.io.Logger

object Export {
  val logger = Logger("export")

  def exportData: IO[Unit] =
    IO.unit

  def run(target: String): IO[ExitCode] =
    target match {
      case "content" => exportData.as(ExitCode.Success)
      case _ =>
        logger.error(s"Unknown export target '$target'").as(ExitCode.Error)
    }
}
