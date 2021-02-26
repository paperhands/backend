package app.paperhands.ocr
import com.typesafe.scalalogging.Logger
import sys.process._
import java.io.File
import scala.io.Source
import sttp.client3._

object OCR {
  val backend = HttpURLConnectionBackend()
  val logger = Logger("ocr")
  val dpi = 150

  def processFile(input: String): String = {
    logger.info(s"processing file $input")
    val cmd = s"tesseract $input stdout --dpi $dpi -l eng"
    cmd.!!
  }

  def processURL(url: String): String = {
    val targetFile = File.createTempFile("ocr-", ".image")
    val target = targetFile.getAbsolutePath()

    logger.info(s"processing url $url -> $target")

    try {
      val request = basicRequest
        .response(asFile(targetFile))
        .get(uri"$url")
        .send(backend)

      processFile(target)
    } catch {
      case e: Throwable =>
        logger.error(s"Error processing URL $url: $e")
        ""
    } finally targetFile.delete()
  }
}
