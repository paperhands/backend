package app.paperhands

import cats._
import cats.effect._
import cats.implicits._
import app.paperhands.scraper.Scraper
import app.paperhands.server.Server
import app.paperhands.flyway.MyFlyway
import app.paperhands.io.Logger

object Main extends IOApp {
  val logger = Logger("main")

  override def run(args: List[String]): IO[ExitCode] =
    args.headOption match {
      case Some("scrape")                    => Scraper.run
      case Some("server")                    => Server.run
      case Some("flyway") if args.length > 1 => MyFlyway.run(args(1))
      case _ =>
        logger
          .error(s"""Unknown command "${args.mkString(" ")}" """)
          .flatMap(_ => IO.pure(ExitCode.Error))
    }
}
