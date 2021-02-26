import org.junit.Test
import org.junit.Assert._
import app.paperhands.scraper.RedditScraper
import scala.io.Source

class ScraperTest {
  @Test def t1(): Unit = {
    assertEquals(
      false,
      RedditScraper
        .isImageURL(
          "https://www.reddit.com/r/wallstreetbets/comments/lt23qd/what_books_on_investments_do_you_suggest/"
        )
    )
  }

  @Test def t2(): Unit = {
    assertEquals(
      true,
      RedditScraper
        .isImageURL(
          "https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png"
        )
    )
  }

  @Test def t3(): Unit = {
    assertEquals(
      "",
      RedditScraper
        .extractURLs(
          """
          no link hear
          """
        )
    )
  }

  @Test def t4(): Unit = {
    assertEquals(
      true,
      RedditScraper
        .extractURLs(
          """
          this is my dope link https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png
          """
        )
        .contains("""
          https://d1wvxg652jdms0.cloudfront.net/diy-dependabot-clojure/pr-preview.png:
          [error] [Automated] Update dependencies #4
          """.stripMargin)
    )
  }
}
