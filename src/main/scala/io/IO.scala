package app.paperhands.io

import cats._
import cats.effect._
import cats.implicits._

import scala.concurrent._

import com.typesafe.scalalogging

trait AddContextShift {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
}

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
