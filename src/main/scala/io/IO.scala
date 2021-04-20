package app.paperhands.io

import cats.effect._
import com.typesafe.scalalogging

case class LoggerWrapper(logger: scalalogging.Logger) {
  def trace(m: String): IO[Unit] =
    IO(logger.trace(m))
  def debug(m: String): IO[Unit] =
    IO(logger.debug(m))
  def info(m: String): IO[Unit] =
    IO(logger.info(m))
  def warn(m: String): IO[Unit] =
    IO(logger.warn(m))
  def error(m: String): IO[Unit] =
    IO(logger.error(m))
}

object Logger {
  def apply(name: String) =
    LoggerWrapper(scalalogging.Logger(name))
}

trait HttpBackend {
  import org.http4s.client.blaze._
  import scala.concurrent.ExecutionContext.global

  val client = BlazeClientBuilder[IO](global).resource
}
