package app.paperhands

import cats._
import cats.effect._
import cats.implicits._
import app.paperhands.scraper.Scraper
import app.paperhands.server.Server
import app.paperhands.flyway.MyFlyway

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    args.headOption match {
      case Some("scrape") => Scraper.run
      case Some("server") => Server.run
      case Some("flyway") => MyFlyway.run(args(1))
      case _ => {
        IO(println("Unknown command"))
          .flatMap(_ => IO.pure(ExitCode.Error))
      }
    }
}
