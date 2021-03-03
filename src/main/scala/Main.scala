package app.paperhands

import cats._
import cats.effect._
import cats.implicits._
import app.paperhands.scraper.Scraper
import app.paperhands.server.Server
import app.paperhands.flyway.MyFlyway
import app.paperhands.io.Logger
import app.paperhands.storage.ConnectionPool

object Main extends IOApp with ConnectionPool {
  val logger = Logger("main")

  override def run(args: List[String]): IO[ExitCode] =
    transactor.use { xa =>
      args.headOption match {
        case Some("scrape")                    => Scraper.run(xa)
        case Some("server")                    => Server.run(xa)
        case Some("flyway") if args.length > 1 => MyFlyway.run(args(1))
        case _ =>
          logger
            .error(s"""Unknown command "${args.mkString(" ")}" """) *>
            IO.pure(ExitCode.Error)
      }
    }
}
