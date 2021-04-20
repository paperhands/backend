import app.paperhands.ocr.OCR
import app.paperhands.config.Config
import scala.concurrent.duration._
import cats.implicits._
import cats.effect.testing.minitest.IOTestSuite

object OCRTestSuite extends IOTestSuite {
  override val timeout = 10.second

  test("process file") {
    val path =
      getClass.getClassLoader.getResource("fixtures/ocr/gme.png").getPath

    for {
      cfg <- Config.cfg
      output <- OCR.processFile(cfg, path)
    } yield assert(output.contains("GME"))
  }

  test("process url") {
    val url =
      "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"

    for {
      cfg <- Config.cfg
      output <- OCR.processURL(cfg, url)
    } yield assert(output.contains("[Automated]"))
  }
}
