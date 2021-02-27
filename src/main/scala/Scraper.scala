package app.paperhands.scraper

import app.paperhands.reddit.{Reddit, Entry}
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

  def handleEntry(e: Entry) =
    preHandle(e)

  def preHandle(e: Entry) = {
    val urls = e.url.filter(isImageURL).toList ++ extractImageURLs(e.body)

    if (urls.length > 0) {
      logger.info(
        s"starting new thread to process ${urls.length} urls"
      )

      val thread = new Thread {
        override def run = {
          val urlsOut = urls.map(processURL).mkString("")
          handle(e.copy(body = s"${e.body}$urlsOut"))
        }
      }

      pool.submit(thread)
    } else {
      handle(e)
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
      entry: Entry,
      symbols: List[String],
      sentiment: model.SentimentValue
  ): List[model.Sentiment] = {
    model.Sentiment.fromSymbols(
      symbols,
      sentiment,
      entry.name
    )
  }

  def handle(entry: Entry) = {
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
