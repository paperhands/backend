package app.paperhands.scraper

import app.paperhands.reddit.{Reddit, RedditComment, RedditPost}
import app.paperhands.config.Config
import app.paperhands.market.Market
import com.typesafe.scalalogging.Logger

case class RedditEntry(
    kind: String,
    id: String,
    name: String,
    author: String,
    permalink: String,
    body: String,
    url: Option[String]
)

object RedditScraper extends Reddit {
  def handleComment(r: RedditComment) = {
    handle(
      RedditEntry(
        r.kind,
        r.id,
        r.name,
        r.author,
        r.permalink,
        r.body,
        None
      )
    )
  }
  def handlePost(r: RedditPost) = {
    handle(
      RedditEntry(
        r.kind,
        r.id,
        r.name,
        r.author,
        r.permalink,
        s"${r.title}\n\n${r.body}",
        r.url
      )
    )
  }

  def handle(entry: RedditEntry) = {
    logger.info(s"$entry")
  }
}

object Scraper {
  def run = {
    val cfg = Config.load
    val market = Market.load
    RedditScraper.loop(cfg.reddit.secret)
  }
}
