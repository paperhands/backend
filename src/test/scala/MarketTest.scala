import minitest._
import app.paperhands.market.Market
import scala.concurrent.duration._
import cats.effect.testing.minitest.IOTestSuite

object MarketTestSuite extends IOTestSuite {
  override val timeout = 10.seconds

  test("load") {
    for {
      m <- Market.market
    } yield assert(m.length >= 9949)
  }

  test("load and vals are present") {
    for {
      m <- Market.market
    } yield {
      var symbs = m.map(_.symbol)
      assertEquals(symbs.find(_ == "Symbol"), None)
      assertEquals(symbs.find(_ == "ZQK"), Some("ZQK"))
    }
  }

}

object MarketCleanupDescTestSuite extends SimpleTestSuite {
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
    t(
      "UWM Holdings Corporation Class A Common Stock",
      "UWM Holdings Corporation"
    )
    t(
      "Pilgrim's Pride Corporation - Common Stock",
      "Pilgrim's Pride Corporation"
    )
  }
}
