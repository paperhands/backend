import org.junit.Test
import org.junit.Assert._
import app.paperhands.ocr.OCR
import scala.io.Source

class OCRTest {
  @Test def t1(): Unit = {
    assertEquals(
      true,
      OCR
        .processFile(
          getClass.getClassLoader.getResource("fixtures/ocr/gme.png").getPath
        )
        .contains("GME")
    )
  }

  @Test def t2(): Unit = {
    assertEquals(
      true,
      OCR
        .processURL(
          "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"
        )
        .contains("[Automated]")
    )
  }
}
