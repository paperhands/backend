package app.paperhands.io

import cats._
import cats.effect._
import cats.implicits._

import cats._
import cats.effect._
import cats.implicits._

import sttp.client3._
import sttp.client3.http4s._

import scala.concurrent._

import com.typesafe.scalalogging

trait AddContextShift {
  implicit val cs: ContextShift[IO] = AddContextShift.cs
}

object AddContextShift {
  val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
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

trait HttpBackend extends AddContextShift {
  val backend =
    Blocker[IO].flatMap(Http4sBackend.usingDefaultBlazeClientBuilder[IO](_))
}
