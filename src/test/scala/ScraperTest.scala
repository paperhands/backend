import minitest._
import app.paperhands.market.Market
import app.paperhands.config.Config
import app.paperhands.storage.ConnectionPool
import app.paperhands.scraper._
import app.paperhands.model
import cats.effect.testing.minitest.IOTestSuite
import cats.effect._
import cats.implicits._

object ImgExtractionTestSuite extends SimpleTestSuite {
  test("is image url") {
    assert(
      !ImgExtraction
        .isImageURL(
          "https://www.reddit.com/r/wallstreetbets/comments/lt23qd/what_books_on_investments_do_you_suggest/"
        )
    )

    assert(
      !ImgExtraction
        .isImageURL(
          "https://www.facebook.com/tindahannibebang01/videos/230375435418803/?vh=e"
        )
    )

    assert(
      ImgExtraction
        .isImageURL(
          "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"
        )
    )
  }

  test("image url extraction from text") {
    assertEquals(
      ImgExtraction.extractImageURLs("no link hear"),
      List()
    )

    val url =
      "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"

    assertEquals(
      ImgExtraction.extractImageURLs(s"this is my dope link $url"),
      List(url)
    )
  }
}

object Helper extends ConnectionPool {
  def test(f: (RedditScraper) => Any): IO[Unit] =
    Config.cfg >>= { cfg =>
      Market.market >>= { market =>
        transactor(cfg).use { xa =>
          val s = new RedditScraper(xa, cfg, market)
          IO(f(s))
        }
      }
    }
}

object ScraperTestSuite extends IOTestSuite {
  test("exception symbol extraction") {
    Helper.test { s =>
      assertEquals(List(), s.getSymbols("no symbols in here"))
      assertEquals(s.getSymbols("i bought GME today"), List("GME"))
      assertEquals(s.getSymbols("did we, GME?"), List("GME"))
      assertEquals(s.getSymbols("some $GME today"), List("GME"))
      assertEquals(s.getSymbols("some $gme today"), List("GME"))
      assertEquals(s.getSymbols("i  gme today"), List("GME"))
      assertEquals(s.getSymbols("GME"), List("GME"))
      assertEquals(s.getSymbols("GME to the moon"), List("GME"))
      assertEquals(s.getSymbols("lets go GME"), List("GME"))
    }
  }

  test("ignored symbol extraction") {
    Helper.test { s =>
      assertEquals(s.getSymbols("this is ETF time"), List())
      assertEquals(
        s.getSymbols("this is EOD CEO YOLO time"),
        List()
      )
    }
  }

  test("normal symbol extraction") {
    Helper.test { s =>
      assertEquals(s.getSymbols("this is $ZQK time"), List("ZQK"))
      assertEquals(s.getSymbols("this is $zqk time"), List("ZQK"))
      assertEquals(s.getSymbols("this is ZQK time"), List("ZQK"))
      assertEquals(s.getSymbols("this is ZQK. time"), List("ZQK"))
      assertEquals(s.getSymbols("this is,ZQK."), List("ZQK"))
      assertEquals(s.getSymbols("this is zqk time"), List())
    }
  }

  test("no symbol extraction") {
    Helper.test { s =>
      assertEquals(s.getSymbols("this is zqkishvery time"), List())
      assertEquals(s.getSymbols("this is AMCOE time"), List())
      assertEquals(s.getSymbols("this is GMEIFY time"), List())
      assertEquals(s.getSymbols("this is BABABA time"), List())
      assertEquals(s.getSymbols("this is VEBA time"), List())
    }
  }

  test("sentiment matching") {
    Helper.test { s =>
      assertEquals(
        s.getSentimentValue("to the mooon 🚀"),
        model.Bull()
      )
      assertEquals(
        s.getSentimentValue("nah im good 🧸"),
        model.Bear()
      )
      assertEquals(
        s.getSentimentValue("to the mooon 🧸🚀"),
        model.Bull()
      )
      assertEquals(
        s.getSentimentValue("dunno"),
        model.Unknown()
      )
    }
  }
}
