package app.paperhands

import app.paperhands.config.Config
import app.paperhands.export.Export
import app.paperhands.flyway.MyFlyway
import app.paperhands.io.Logger
import app.paperhands.market.Market
import app.paperhands.scraper.Scraper
import app.paperhands.server.Server
import app.paperhands.storage.ConnectionPool
import cats.effect._
import cats.implicits._

object Main extends IOApp with ConnectionPool {

  val logger = Logger("main")

  override def run(args: List[String]): IO[ExitCode] =
    Config.cfg >>= { cfg =>
      Market.market >>= { market =>
        transactor(cfg).use { xa =>
          args.headOption match {
            case Some("scrape") => Scraper.run(xa, cfg, market)
            case Some("server") => new Server(xa, cfg, market).run
            case Some("export") if args.length > 1 =>
              Export.run(args.get(1), args.get(2), xa)
            case Some("flyway") if args.length > 1 =>
              MyFlyway.run(xa, args.get(1))
            case _ =>
              logger
                .error(s"Unknown command '${args.mkString(" ")}'")
                .as(ExitCode.Error)
          }
        }
      }
    }
}
