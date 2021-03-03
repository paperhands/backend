package app.paperhands.server

import app.paperhands.io.AddContextShift
import app.paperhands.config.Cfg
import app.paperhands.handlers.paperhands

import cats._
import cats.effect._
import cats.implicits._

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import scala.concurrent.ExecutionContext.Implicits.global
import doobie.hikari.HikariTransactor

object Server extends Cfg with AddContextShift {
  implicit val timer: Timer[IO] = IO.timer(global)

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
