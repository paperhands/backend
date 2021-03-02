import minitest._
import app.paperhands.market.Market

object MarketTestSuite extends SimpleTestSuite {
  def t(origin: String, expectation: String) =
    assertEquals(
      Market.cleanupDescription(origin),
      expectation
    )

  test("cleanupDescription") {
    t(
      "Artius Acquisition Inc. - Warrant",
      "Artius Acquisition Inc."
    )
    t(
      "ABIOMED, Inc. - Common Stock",
      "ABIOMED, Inc."
    )
    t(
      "Artius Acquisition Inc. - Class A Common Stock",
      "Artius Acquisition Inc."
    )
  }
}
