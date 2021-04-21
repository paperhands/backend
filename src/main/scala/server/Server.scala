package app.paperhands.server

import app.paperhands.config.Config
import app.paperhands.handlers.paperhands
import app.paperhands.market.Market
import cats.effect._
import doobie.hikari.HikariTransactor
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze._

import scala.concurrent.ExecutionContext.Implicits.global

class Server(xa: HikariTransactor[IO], cfg: Config, market: Market.Market) {
  def httpApp =
    Router(
      "/api/v1" -> new paperhands.Handler(xa, cfg, market).paperhandsService
    ).orNotFound

  def serverBuilder =
    BlazeServerBuilder[IO](global)
      .bindHttp(cfg.http.port, cfg.http.host)
      .withHttpApp(httpApp)

  def start =
    serverBuilder.resource.use(_ => IO.never)

  def run: IO[ExitCode] =
    start.as(ExitCode.Success)
}
