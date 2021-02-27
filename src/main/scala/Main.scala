package app.paperhands

import cats.effect._
import cats.syntax.all._
import app.paperhands.scraper.Scraper

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <-
        Scraper.run
    } yield ExitCode.Success
}
