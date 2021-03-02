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
    t(
      "Rocket Companies, Inc. Class A Common Stock",
      "Rocket Companies, Inc."
    )
    t(
      "AMC Entertainment Holdings, Inc. Class A Common Stock",
      "AMC Entertainment Holdings, Inc."
    )
    t(
      "GameStop Corporation Common Stock",
      "GameStop Corporation"
    )
    t(
      "NIO Inc. American depositary shares, each  representing one Class A ordinary share",
      "NIO Inc."
    )
    t(
      "Grocery Outlet Holding Corp. - Common Stock",
      "Grocery Outlet Holding Corp."
    )
  }
}
