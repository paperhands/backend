package app.paperhands.scraper

import app.paperhands.reddit.{Reddit, RedditComment, RedditPost}
import app.paperhands.config.Config
import app.paperhands.market.Market
import app.paperhands.ocr.OCR
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
  def processURL(url: String): String = {
    val out = OCR.processURL(url)
    s"\n$url:\n$out"
  }

  val imgPattern = "^.*\\.(png|jpg|jpeg|gif)$".r
  def isImageURL(url: String): Boolean = {
    imgPattern.matches(url)
  }

  val urlPattern =
    "(?:https?:\\/\\/)(?:\\w+(?:-\\w+)*\\.)+\\w+(?:-\\w+)*\\S*?(?=[\\s)]|$)".r
  def extractURLs(body: String): String = {

    urlPattern
      .findAllIn(body)
      .toList
      .filter(isImageURL)
      .map(processURL)
      .mkString("")
  }

  def handleComment(r: RedditComment) = {
    val bodyOut = extractURLs(r.body)

    handle(
      RedditEntry(
        r.kind,
        r.id,
        r.name,
        r.author,
        r.permalink,
        s"${r.body}$bodyOut",
        None
      )
    )
  }
  def handlePost(r: RedditPost) = {
    val urlOut = r.url.filter(isImageURL).map(processURL)
    val bodyOut = extractURLs(r.body)

    handle(
      RedditEntry(
        r.kind,
        r.id,
        r.name,
        r.author,
        r.permalink,
        s"${r.title}\n\n${r.body}$urlOut$bodyOut",
        r.url
      )
    )
  }

  def handle(entry: RedditEntry) = {
    logger.info(s"${entry.author}: ${entry.body}")
  }
}

object Scraper {
  def run = {
    val cfg = Config.load
    val market = Market.load
    RedditScraper.loop(cfg.reddit.secret)
  }
}
