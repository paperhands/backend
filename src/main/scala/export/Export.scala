package app.paperhands.export

import app.paperhands.io.Logger
import app.paperhands.model
import app.paperhands.storage.Storage
import cats.data._
import cats.effect._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import fs2._
import fs2.data.csv._
import fs2.io.file._

import java.nio.file.Paths

object Export {
  val logger = Logger("export")

  val header = NonEmptyList.of(
    "id",
    "type",
    "source",
    "parent_id",
    "permalink",
    "body",
    "created_time",
    "symbols",
    "sentiment"
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
        c.parsed.symbols.mkString(" "),
        c.parsed.sentiment.toString
      )
    )

  def writeStream(stream: Stream[IO, Row], f: String) =
    stream
      .through(writeWithHeaders(header))
      .through(toRowStrings(separator = ',', newline = "\n"))
      .through(text.utf8Encode)
      .through(writeAll(Paths.get(f)))
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
