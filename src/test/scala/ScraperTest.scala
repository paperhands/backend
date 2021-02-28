import minitest._
import app.paperhands.scraper.RedditScraper
import app.paperhands.model

object ScraperTestSuite extends SimpleTestSuite {
  test("is image url") {
    assert(
      !RedditScraper
        .isImageURL(
          "https://www.reddit.com/r/wallstreetbets/comments/lt23qd/what_books_on_investments_do_you_suggest/"
        )
    )

    assert(
      !RedditScraper
        .isImageURL(
          "https://www.facebook.com/tindahannibebang01/videos/230375435418803/?vh=e"
        )
    )

    assert(
      RedditScraper
        .isImageURL(
          "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"
        )
    )
  }

  test("image url extraction from text") {
    assertEquals(
      RedditScraper.extractImageURLs("no link hear"),
      List()
    )

    val url =
      "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"

    assertEquals(
      RedditScraper.extractImageURLs(s"this is my dope link $url"),
      List(url)
    )
  }

  test("exception symbol extraction") {
    assertEquals(List(), RedditScraper.getSymbols("no symbols in here"))
    assertEquals(
      RedditScraper.getSymbols("i bought some GME today"),
      List("GME")
    )
    assertEquals(
      RedditScraper.getSymbols("i bought some $GME today"),
      List("GME")
    )
    assertEquals(
      RedditScraper.getSymbols("i bought some $gme today"),
      List("GME")
    )
    assertEquals(
      RedditScraper.getSymbols("i bought some gme today"),
      List("GME")
    )
    assertEquals(
      RedditScraper.getSymbols("GME"),
      List("GME")
    )
    assertEquals(
      RedditScraper.getSymbols("GME to the moon"),
      List("GME")
    )
    assertEquals(
      RedditScraper.getSymbols("lets go GME"),
      List("GME")
    )
  }

  test("ignored symbol extraction") {
    assertEquals(RedditScraper.getSymbols("this is ETF time"), List())
    assertEquals(RedditScraper.getSymbols("this is EOD CEO YOLO time"), List())
  }

  test("normal symbol extraction") {
    assertEquals(RedditScraper.getSymbols("this is $ZQK time"), List("ZQK"))
    assertEquals(RedditScraper.getSymbols("this is $zqk time"), List("ZQK"))
    assertEquals(RedditScraper.getSymbols("this is ZQK time"), List("ZQK"))
    assertEquals(RedditScraper.getSymbols("this is zqk time"), List())
  }

  test("sentiment matching") {
    assertEquals(
      RedditScraper.getSentimentValue("to the mooon 🚀"),
      model.Bull()
    )
    assertEquals(
      RedditScraper.getSentimentValue("nah im good 🧸"),
      model.Bear()
    )
    assertEquals(
      RedditScraper.getSentimentValue("to the mooon 🧸🚀"),
      model.Bull()
    )
    assertEquals(
      RedditScraper.getSentimentValue("dunno"),
      model.Unknown()
    )
  }
}
