package app.paperhands.ocr
import sys.process._
import java.io.File

import sttp.client3._
import sttp.client3.http4s._

import cats._
import cats.effect._
import cats.implicits._

import scala.concurrent._

import app.paperhands.io.{Logger, AddContextShift, HttpBackend}
import app.paperhands.config.Cfg

object OCR extends AddContextShift with HttpBackend with Cfg {
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
    logger.debug(s"processing file $input") *> runTesseract(input)

  def tmpFile(prefix: String, postfix: String): Resource[IO, File] =
    Resource.make {
      IO(File.createTempFile(prefix, postfix))
    } { f =>
      IO(f.delete())
        .handleErrorWith(e => logger.error(s"Could not delete tmp file: $e"))
        .void
    }

  def constructRequest(
      url: String,
      file: File
  ): Request[Either[String, File], Any with Any] =
    basicRequest
      .response(asFile(file))
      .get(uri"$url")

  def shouldProcess(response: Response[Either[String, File]]) =
    response
      .header("Content-Type")
      .filter(_.startsWith("image/"))
      .isDefined

  def processURL(url: String): IO[String] =
    (tmpFile("ocr-", ".image"), backend).tupled.use { case (tmpF, backend) =>
      logger.info(s"processing url $url -> ${tmpF.getAbsolutePath}") *>
        constructRequest(url, tmpF)
          .send(backend)
          .map(shouldProcess)
          .ifM(processFile(tmpF.getAbsolutePath), IO.pure(""))
    }
}
