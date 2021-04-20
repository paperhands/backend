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
      assertEquals(gmes.length, 2),
      assertEquals(gmes.head.symbol, "GME")
    )
  }

  test("findByDesc") {
    for {
      market <- Market.market
      gmes <- IO.pure(SearchQuote.findByDesc(market, "Gamestop"))
    } yield (
      assertEquals(gmes.length, 3),
      assertEquals(gmes.head.symbol, "GME")
    )
  }

  test("findByDesc") {
    for {
      market <- Market.market
      gmes <- IO.pure(SearchQuote.findByDesc(market, "Gumestonp"))
    } yield (
      assertEquals(gmes.length, 1),
      assertEquals(gmes.head.symbol, "GME")
    )
  }
}
