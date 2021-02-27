package app.paperhands.scraper

import app.paperhands.reddit.{Reddit, Entry}
import app.paperhands.config.{Config, Cfg}
import app.paperhands.market.Market
import app.paperhands.ocr.OCR
import app.paperhands.model
import app.paperhands.storage.Storage
import com.typesafe.scalalogging.Logger
import java.util.concurrent.Executors

import cats._
import cats.effect._
import cats.implicits._

object RedditScraper extends Reddit with Cfg with Market {
  val imgPattern = "^.*\\.(png|jpg|jpeg|gif)$".r
  val urlPattern =
    "(?:https?:\\/\\/)(?:\\w+(?:-\\w+)*\\.)+\\w+(?:-\\w+)*\\S*?(?=[\\s)]|$)".r
  val pool = Executors.newFixedThreadPool(cfg.reddit.thread_pool)

  def processURL(url: String): IO[String] = {
    for {
      out <- IO(OCR.processURL(url))
    } yield (s"\n$url:\n$out")
  }

  def processURLs(urls: List[String]): IO[String] = {
    for {
      url <- urls.traverse(processURL)
    } yield (url.mkString(""))
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

  def handleEntry(e: Entry): IO[Unit] = {
    val urls = e.url.filter(isImageURL).toList ++ extractImageURLs(e.body)

    if (urls.length > 0) {
      logger.info(
        s"starting new thread to process ${urls.length} urls"
      )

      for {
        out <- processURLs(urls).as("OCR fibre")
      } yield (handle(e.copy(body = s"${e.body}$out")))
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

  def handle(entry: Entry): IO[Unit] = {
    val symbols = getSymbols(entry.body)
    val sentimentVal = getSentimentValue(entry.body)
    val sentiments = sentimentFor(entry, symbols, sentimentVal)
    val content =
      model.Content.fromRedditEntry(entry, symbols, sentimentVal)

    Blocker[IO].use { blocker =>
      for {
        _ <-
          blocker.blockOn(
            IO(logger.info(s"${entry.author}: ${entry.body} $sentiments"))
          )
        _ <- blocker.blockOn(Storage.saveSentiments(sentiments))
        _ <- blocker.blockOn(Storage.saveContent(content))
      } yield ()
    }
  }
}

object Scraper extends Cfg {
  def run =
    RedditScraper.loop(cfg.reddit.secret)
}
