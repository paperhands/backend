import minitest._
import app.paperhands.ocr.OCR
import scala.io.Source
import scala.concurrent.duration._
import cats.implicits._
import cats.effect.IO
import cats.effect.testing.minitest.{IOTestSuite, DeterministicIOTestSuite}

object OCRTestSuite extends IOTestSuite {
  override val timeout = 10.second

  test("process file") {
    val path =
      getClass.getClassLoader.getResource("fixtures/ocr/gme.png").getPath

    for {
      output <- OCR.processFile(path)
    } yield (assert(output.contains("GME")))
  }

  test("process url") {
    val url =
      "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"

    for {
      output <- OCR.processURL(url)
    } yield (assert(output.contains("[Automated]")))
  }
}
