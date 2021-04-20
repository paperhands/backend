package app.paperhands.ocr
import app.paperhands.config.Cfg
import app.paperhands.io.HttpBackend
import app.paperhands.io.Logger
import cats.effect._
import cats.implicits._
import fs2.io.file._
import org.http4s._

import java.io.File
import java.nio.file.Paths

import sys.process._

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
