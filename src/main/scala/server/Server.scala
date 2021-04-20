package app.paperhands.server

import app.paperhands.config.Cfg
import app.paperhands.handlers.paperhands
import cats.effect._
import doobie.hikari.HikariTransactor
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze._

import scala.concurrent.ExecutionContext.Implicits.global

object Server extends Cfg {
  def httpApp(xa: HikariTransactor[IO]) = Router(
    "/api/v1" -> paperhands.Handler.paperhandsService(xa)
  ).orNotFound

  def serverBuilder(xa: HikariTransactor[IO]) = BlazeServerBuilder[IO](global)
    .bindHttp(cfg.http.port, cfg.http.host)
    .withHttpApp(httpApp(xa))

  def start(xa: HikariTransactor[IO]) =
    serverBuilder(xa).resource.use(_ => IO.never)

  def run(xa: HikariTransactor[IO]): IO[ExitCode] =
    start(xa).as(ExitCode.Success)
}
