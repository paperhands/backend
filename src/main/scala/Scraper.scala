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

import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts

import monocle.macros.syntax.all._

object RedditScraper extends Reddit with Cfg with Market {
  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    s"jdbc:postgresql:${cfg.repository.database}",
    cfg.repository.user,
    cfg.repository.password
  )

  val imgPattern = "^.*\\.(png|jpg|jpeg|gif)$".r
  val urlPattern =
    "(?:https?:\\/\\/)(?:\\w+(?:-\\w+)*\\.)+\\w+(?:-\\w+)*\\S*?(?=[\\s)]|$)".r
  val pool = Executors.newFixedThreadPool(cfg.reddit.thread_pool)

  def processURL(url: String): IO[String] =
    for {
      out <- IO(OCR.processURL(url))
    } yield (s"\n$url:\n$out")

  def processURLs(urls: List[String]): IO[String] =
    for {
      url <- urls.traverse(processURL)
    } yield (url.mkString(""))

  def isImageURL(url: String): Boolean =
    imgPattern.matches(url)

  def extractImageURLs(body: String): List[String] =
    urlPattern
      .findAllIn(body)
      .toList
      .filter(isImageURL)

  def handleEntry(e: Entry): IO[Unit] = {
    val urls = e.url.filter(isImageURL).toList ++ extractImageURLs(e.body)

    if (urls.length > 0) {
      logger.info(
        s"starting new thread to process ${urls.length} urls"
      )

      for {
        out <- processURLs(urls).as("OCR fibre")
      } yield (handle(e.focus(_.body).modify(v => s"$v$out")))
    } else {
      handle(e)
    }
  }

  def sentTestFn(body: String, coll: List[String]): Boolean =
    coll.find(s => body.contains(s)).isDefined

  def getSentimentValue(body: String): model.SentimentValue =
    (
      sentTestFn(body, cfg.sentiment.bull),
      sentTestFn(body, cfg.sentiment.bear)
    ) match {
      case (true, true)   => model.Bull()
      case (true, false)  => model.Bull()
      case (false, true)  => model.Bear()
      case (false, false) => model.Unknown()
    }

  def isException(symb: String): Boolean =
    cfg.market.exceptions.find(_ == symb).isDefined

  def isIgnored(symb: String): Boolean =
    cfg.market.ignores.find(_ == symb).isDefined

  def getSymbols(body: String): List[String] =
    market
      .map(_.symbol)
      .filter(s => {
        val exceptionRe = s"(?is).*\\s+$s\\b.*".r
        val normalRe = s"(?is).*\\s*\\$$$s\\b.*".r
        val desperationRe = s"(?s).*\\b+$s\\b.*".r
        // Logic here is if its an exception symbol just match for GME
        // otherwise try to match for $GME
        // if this did not work make sure its not an exception
        // and length is greater than 1
        // and match just GME but with \b as word bounds market
        // and case sensitive
        // \b does not work at the end or beginning of string
        (isException(s) && exceptionRe.matches(body)) ||
        (normalRe.matches(body)) ||
        (!isIgnored(s) &&
          s.length() > 1 &&
          desperationRe.matches(body))
      })

  def sentimentFor(
      entry: Entry,
      symbols: List[String],
      sentiment: model.SentimentValue
  ): List[model.Sentiment] =
    model.Sentiment.fromSymbols(
      symbols,
      sentiment,
      entry.name
    )

  def engagementFor(
      entry: Entry,
      symbols: List[String]
  ): List[model.Engagement] =
    model.Engagement.fromSymbols(
      symbols,
      entry.name
    )

  def symbolsFromTree(tree: List[model.ContentMeta]): List[String] =
    tree.map(_.symbols).flatten.distinct

  def extractTreeSymbols(id: Option[String]): IO[List[String]] =
    id match {
      case Some(id) =>
        for {
          tree <- Storage.getParsedTree(id).transact(xa)
        } yield (symbolsFromTree(tree))
      case None => IO(List())
    }

  def handle(entry: Entry): IO[Unit] = {
    val symbols = getSymbols(entry.body)
    val sentimentVal = getSentimentValue(entry.body)
    val sentiments = sentimentFor(entry, symbols, sentimentVal)
    val content =
      model.Content.fromRedditEntry(entry, symbols, sentimentVal)

    for {
      _ <- Storage.saveContent(content).transact(xa)
      _ <- Storage.saveSentiments(sentiments).transact(xa)
      treeSymbols <- extractTreeSymbols(entry.parent_id)
      engagements <- IO.pure(
        engagementFor(entry, (symbols ++ treeSymbols).distinct)
      )
      _ <-
        IO(
          logger.info(
            s"${entry.author}: ${entry.body}\nsenti: $sentiments\nenga: $engagements"
          )
        )
      _ <- Storage
        .saveEngagements(engagements)
        .transact(xa)
    } yield ()
  }
}

object Scraper extends Cfg {
  def run =
    RedditScraper.loop(cfg.reddit.secret)
}
