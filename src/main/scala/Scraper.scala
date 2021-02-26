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
    url: Option[String],
    imageURLs: List[String]
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
  def extractImageURLs(body: String): List[String] = {

    urlPattern
      .findAllIn(body)
      .toList
      .filter(isImageURL)
  }

  def handleComment(r: RedditComment) = {
    val urls = extractImageURLs(r.body)

    preHandle(
      RedditEntry(
        r.kind,
        r.id,
        r.name,
        r.author,
        r.permalink,
        s"${r.body}",
        None,
        urls
      )
    )
  }
  def handlePost(r: RedditPost) = {
    val urls = r.url.filter(isImageURL).toList ++ extractImageURLs(r.body)

    preHandle(
      RedditEntry(
        r.kind,
        r.id,
        r.name,
        r.author,
        r.permalink,
        s"${r.title}\n\n${r.body}",
        r.url,
        urls
      )
    )
  }

  def preHandle(entry: RedditEntry) = {
    if (entry.imageURLs.length > 0) {
      logger.info(
        s"starting new thread to process ${entry.imageURLs.length} urls"
      )

      new Thread {
        override def run = {
          val urlsOut = entry.imageURLs.map(processURL).mkString("")

          logger.info(s"Result of media processing: $urlsOut")

          handle(entry.copy(body = s"${entry.body}$urlsOut"))
        }
      }.start
    } else {
      handle(entry)
    }
  }

  def handle(entry: RedditEntry) = {
    // logger.info(s"${entry.author}: ${entry.body}")
  }
}

object Scraper {
  def run = {
    val cfg = Config.load
    val market = Market.load
    RedditScraper.loop(cfg.reddit.secret)
  }
}
