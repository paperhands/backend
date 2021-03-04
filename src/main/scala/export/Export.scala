package app.paperhands.export

import cats._
import cats.effect._
import cats.implicits._
import cats.data._

import fs2._
import fs2.data.csv._
import fs2.io.file._
import java.nio.file.Paths

import doobie._
import doobie.implicits._
import doobie.hikari.HikariTransactor

import app.paperhands.io.{Logger, AddContextShift}
import app.paperhands.storage.Storage
import app.paperhands.model
import java.nio.file.Paths

object Export extends AddContextShift {
  implicit val ioTimer: Timer[IO] =
    IO.timer(scala.concurrent.ExecutionContext.Implicits.global)

  val blocker: Blocker =
    Blocker.liftExecutionContext(
      scala.concurrent.ExecutionContext.Implicits.global
    )

  val logger = Logger("export")

  val header = NonEmptyList.of(
    "id",
    "type",
    "source",
    "parent_id",
    "permalink",
    "body",
    "created_time",
    "symbols"
  )

  def contentToRow(c: model.Content) =
    Row(
      NonEmptyList.of(
        c.id,
        c.kind,
        c.source,
        c.parent_id.mkString,
        c.permalink,
        c.body,
        c.created_time.toString,
        c.parsed.symbols.mkString(" ")
      )
    )

  def writeStream(stream: Stream[IO, Row], f: String) =
    stream
      .through(writeWithHeaders(header))
      .through(toRowStrings(separator = ',', newline = "\n"))
      .through(text.utf8Encode)
      .through(writeAll(Paths.get(f), blocker))
      .compile
      .drain

  def exportData(f: String, xa: HikariTransactor[IO]): IO[ExitCode] =
    writeStream(
      Storage.getContentForExport
        .transact(xa)
        .map(contentToRow),
      f
    )
      .as(ExitCode.Success)

  def run(
      target: Option[String],
      f: Option[String],
      xa: HikariTransactor[IO]
  ): IO[ExitCode] =
    target match {
      case Some("content") =>
        exportData(f.getOrElse("/tmp/paperhands-content-export.csv"), xa)
      case _ =>
        logger
          .error(s"Unknown export target '$target'")
          .as(ExitCode.Error)
    }
}
