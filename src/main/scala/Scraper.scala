package app.paperhands.scraper

import app.paperhands.reddit.{Reddit, RedditComment, RedditPost}
import app.paperhands.config.Config
import app.paperhands.market.Market
import app.paperhands.ocr.OCR
import com.typesafe.scalalogging.Logger
import java.util.concurrent.Executors

trait Sentiment
case class Unknown() extends Sentiment
case class Bull() extends Sentiment
case class Bear() extends Sentiment

case class SentimentData(
    sentiment: Sentiment,
    symbols: List[String]
)

case class RedditEntry(
    kind: String,
    id: String,
    name: String,
    author: String,
    permalink: String,
    body: String,
    parent_id: Option[String],
    url: Option[String],
    imageURLs: List[String]
)

object RedditScraper extends Reddit {
  val cfg = Config.load
  val market = Market.load

  val imgPattern = "^.*\\.(png|jpg|jpeg|gif)$".r
  val urlPattern =
    "(?:https?:\\/\\/)(?:\\w+(?:-\\w+)*\\.)+\\w+(?:-\\w+)*\\S*?(?=[\\s)]|$)".r
  val pool = Executors.newFixedThreadPool(cfg.reddit.thread_pool)

  def processURL(url: String): String = {
    val out = OCR.processURL(url)
    s"\n$url:\n$out"
  }

  def isImageURL(url: String): Boolean = {
    imgPattern.matches(url)
  }

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
        Some(r.parent_id),
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
        None,
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

      val thread = new Thread {
        override def run = {
          val urlsOut = entry.imageURLs.map(processURL).mkString("")
          logger.info(s"Result of media processing: $urlsOut")
          handle(entry.copy(body = s"${entry.body}$urlsOut"))
        }
      }

      pool.submit(thread)
    } else {
      handle(entry)
    }
  }

  def sentTestFn(body: String, coll: List[String]): Boolean = {
    coll.find(s => body.contains(s)).isDefined
  }

  def sentiment(body: String): Sentiment = {
    (
      sentTestFn(body, cfg.sentiment.bull),
      sentTestFn(body, cfg.sentiment.bear)
    ) match {
      case (true, true)   => Bear()
      case (true, false)  => Bear()
      case (false, true)  => Bull()
      case (false, false) => Unknown()
    }
  }

  def isException(symb: String): Boolean = {
    cfg.market.exceptions.find(_ == symb).isDefined
  }

  def isIgnored(symb: String): Boolean = {
    cfg.market.ignores.find(_ == symb).isDefined
  }

  def symbols(body: String): List[String] = {
    market
      .map(_.symbol)
      .filter(s => {
        if (isException(s))
          s"(?i).*\\s*$s\\b.*".r.matches(body)
        else if (isIgnored(s) || s.length() == 1)
          false
        else
          s"(?i).*\\s*\\$$$s\\b.*".r.matches(body)
      })
  }

  def sentimentFor(entry: RedditEntry): SentimentData = {
    SentimentData(sentiment(entry.body), symbols(entry.body))
  }

  def handle(entry: RedditEntry) = {
    logger.info(s"${entry.author}: ${entry.body} ${sentimentFor(entry)}")
  }
}

object Scraper {
  def run = {
    val cfg = Config.load
    RedditScraper.loop(cfg.reddit.secret)
  }
}
