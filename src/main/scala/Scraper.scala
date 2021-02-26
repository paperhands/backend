package app.paperhands.scraper

import app.paperhands.reddit.{Reddit, RedditComment, RedditPost}
import app.paperhands.config.{Config, Cfg}
import app.paperhands.market.Market
import app.paperhands.ocr.OCR
import app.paperhands.model
import app.paperhands.storage.Storage
import com.typesafe.scalalogging.Logger
import java.util.concurrent.Executors

object RedditScraper extends Reddit with Cfg with Market {
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
      model.RedditEntry(
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
      model.RedditEntry(
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

  def preHandle(entry: model.RedditEntry) = {
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

  def getSentimentValue(body: String): model.SentimentValue = {
    (
      sentTestFn(body, cfg.sentiment.bull),
      sentTestFn(body, cfg.sentiment.bear)
    ) match {
      case (true, true)   => model.Bear()
      case (true, false)  => model.Bear()
      case (false, true)  => model.Bull()
      case (false, false) => model.Unknown()
    }
  }

  def isException(symb: String): Boolean = {
    cfg.market.exceptions.find(_ == symb).isDefined
  }

  def isIgnored(symb: String): Boolean = {
    cfg.market.ignores.find(_ == symb).isDefined
  }

  def getSymbols(body: String): List[String] = {
    market
      .map(_.symbol)
      .filter(s => {
        if (isException(s))
          s"(?is).*\\s*$s\\b.*".r.matches(body)
        else if (isIgnored(s) || s.length() == 1)
          false
        else
          s"(?is).*\\s*\\$$$s\\b.*".r.matches(body)
      })
  }

  def sentimentFor(
      entry: model.RedditEntry,
      symbols: List[String],
      sentiment: model.SentimentValue
  ): List[model.Sentiment] = {
    model.Sentiment.fromSymbols(
      symbols,
      sentiment,
      entry.name
    )
  }

  def handle(entry: model.RedditEntry) = {
    val symbols = getSymbols(entry.body)
    val sentimentVal = getSentimentValue(entry.body)
    val sentiments = sentimentFor(entry, symbols, sentimentVal)
    val content =
      model.Content.fromRedditEntry(entry, symbols, sentimentVal)
    logger.info(s"${entry.author}: ${entry.body} $sentiments")
    Storage.saveSentiments(sentiments)
    Storage.saveContent(content)
  }
}

object Scraper extends Cfg {
  def run = {
    RedditScraper.loop(cfg.reddit.secret)
  }
}
