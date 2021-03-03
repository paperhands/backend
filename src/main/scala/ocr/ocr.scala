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

object OCR extends AddContextShift with HttpBackend {
  val logger = Logger("ocr")
  val dpi = "72"

  def processFile(input: String): IO[String] = {
    IO(
      Process(
        Seq("tesseract", input, "stdout", "--dpi", dpi, "-l", "eng"),
        None,
        "OMP_THREAD_LIMIT" -> "1"
      ).!!
    )
      .handleErrorWith(e =>
        logger.error(
          s"Error processing $input: $e\n${e.getStackTrace.mkString("\n")}"
        ) *> IO.pure("")
      )
  }

  def tmpFile(prefix: String, postfix: String): Resource[IO, File] =
    Resource.make {
      IO(File.createTempFile(prefix, postfix))
    } { f =>
      IO(f.delete())
    }

  def constructRequest(
      url: String,
      file: File
  ): IO[Request[Either[String, File], Any with Any]] =
    IO(
      basicRequest
        .response(asFile(file))
        .get(uri"$url")
    )

  def processURL(url: String): IO[String] =
    (tmpFile("ocr-", ".image"), backend).tupled.use { case (tmpF, backend) =>
      for {
        _ <- logger.info(s"processing url $url -> ${tmpF.getAbsolutePath}")
        request <- constructRequest(url, tmpF)
        response <- request.send(backend)
        shouldProcess <- IO(
          response
            .header("Content-Type")
            .filter(_.startsWith("image/"))
            .isDefined
        )
        result <-
          if (shouldProcess) processFile(tmpF.getAbsolutePath) else IO("")
      } yield (result)
    }
}
