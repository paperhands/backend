import minitest._
import app.paperhands.handlers.paperhands._

object SearchQuoteTestSuite extends SimpleTestSuite {
  test("findBySymbol") {
    val gmes = SearchQuote.findBySymbol("GME")
    assertEquals(gmes.length, 2)
    assertEquals(gmes.head.symbol, "GME")
  }

  test("findByDesc correct") {
    val gmes = SearchQuote.findByDesc("Gamestop")
    assertEquals(gmes.length, 3)
    assertEquals(gmes.head.symbol, "GME")
  }

  test("findByDesc fuzzy") {
    val gmes = SearchQuote.findByDesc("Gumestonp")
    assertEquals(gmes.length, 1)
    assertEquals(gmes.head.symbol, "GME")
  }
}
