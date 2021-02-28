import minitest._
import app.paperhands.scraper.RedditScraper

object ScraperTestSuite extends SimpleTestSuite {
  test("is image url") {
    assertEquals(
      false,
      RedditScraper
        .isImageURL(
          "https://www.reddit.com/r/wallstreetbets/comments/lt23qd/what_books_on_investments_do_you_suggest/"
        )
    )

    assertEquals(
      false,
      RedditScraper
        .isImageURL(
          "https://www.facebook.com/tindahannibebang01/videos/230375435418803/?vh=e"
        )
    )

    assertEquals(
      true,
      RedditScraper
        .isImageURL(
          "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"
        )
    )
  }

  test("image url extraction from text") {
    assertEquals(
      List(),
      RedditScraper.extractImageURLs("no link hear")
    )

    val url =
      "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"

    assertEquals(
      List(url),
      RedditScraper.extractImageURLs(s"this is my dope link $url")
    )
  }
}
