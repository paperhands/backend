import app.paperhands.handlers.paperhands._
import app.paperhands.market._
import cats.effect._
import cats.effect.testing.minitest.IOTestSuite

object SearchQuoteTestSuite extends IOTestSuite {
  test("findBySymbol") {
    for {
      market <- Market.market
      gmes <- IO.pure(SearchQuote.findBySymbol(market, "GME"))
    } yield (
      assertEquals(gmes.head.symbol, "GME"),
      assertEquals(gmes.length, 1)
    )
  }

  test("findByDesc") {
    for {
      market <- Market.market
      gmes <- IO.pure(SearchQuote.findByDesc(market, "Gamestop"))
    } yield (
      assertEquals(gmes.head.symbol, "GME"),
      assertEquals(gmes.length, 3)
    )
  }

  test("findByDesc") {
    for {
      market <- Market.market
      gmes <- IO.pure(SearchQuote.findByDesc(market, "Gumestonp"))
    } yield (
      assertEquals(gmes.head.symbol, "GME"),
      assertEquals(gmes.length, 1)
    )
  }
}
