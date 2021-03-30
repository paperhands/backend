package app.paperhands.ocr
import sys.process._
import java.io.File

import fs2._
import fs2.io.file._
import java.nio.file.Paths

import org.http4s._
import org.http4s.implicits._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.MediaType
import org.http4s.Method._

import cats._
import cats.effect._
import cats.implicits._

import scala.concurrent._

import app.paperhands.io.{Logger, HttpBackend}
import app.paperhands.config.Cfg

object OCR extends HttpBackend with Cfg {
  val logger = Logger("ocr")
  val dpi = "72"

  def newProc(input: String) =
    Process(
      Seq(cfg.tesseract.command, input, "stdout", "--dpi", dpi, "-l", "eng"),
      None,
      "OMP_THREAD_LIMIT" -> "1"
    )

  def runTesseract(input: String) =
    IO(newProc(input).!!)
      .handleErrorWith(e =>
        logger
          .error(
            s"Error processing $input: $e\n${e.getStackTrace.mkString("\n")}"
          )
          .as("")
      )

  def processFile(input: String): IO[String] =
    logger.debug(s"processing file $input") >> runTesseract(input)

  def tmpFile(prefix: String, postfix: String): Resource[IO, File] =
    Resource.make {
      IO(File.createTempFile(prefix, postfix))
    } { f =>
      val path = f.getAbsolutePath

      IO(f.delete())
        .handleErrorWith(e =>
          logger.error(s"Could not delete tmp file $path: $e")
        )
        .void
    }

  def processURL(url: String): IO[String] =
    (tmpFile("ocr-", ".image"), client).tupled.use { case (tmpF, client) =>
      val path = tmpF.getAbsolutePath
      val uri = Uri.unsafeFromString(url)

      logger.info(s"processing url $url -> $path") >>
        client
          .get[Boolean](uri)(response =>
            response.contentType
              .filter(_.mediaType.isImage)
              .map(_ =>
                response.body
                  .through(writeAll(Paths.get(path)))
                  .compile
                  .drain
                  .as(true)
              )
              .getOrElse(IO.pure(false))
          )
          .ifM(processFile(path), IO.pure(""))
    }
}
