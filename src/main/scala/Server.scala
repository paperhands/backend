package app.paperhands.server

import app.paperhands.config.Cfg

import cats._
import cats.effect._
import cats.implicits._

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import scala.concurrent.ExecutionContext.Implicits.global

object Server extends Cfg {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

  val paperhandsService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }

  val httpApp = Router("/api" -> paperhandsService).orNotFound
  val serverBuilder = BlazeServerBuilder[IO](global)
    .bindHttp(8080, "localhost")
    .withHttpApp(httpApp)

  def run: IO[ExitCode] =
    for {
      fibre <-
        serverBuilder.resource.use(_ => IO.never).start
      _ <- fibre.join
    } yield (ExitCode.Success)
}
