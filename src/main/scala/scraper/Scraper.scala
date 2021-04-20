package app.paperhands.scraper

import app.paperhands.config.Config
import app.paperhands.market.Market
import app.paperhands.model
import app.paperhands.ocr.OCR
import app.paperhands.reddit.Entry
import app.paperhands.reddit.Reddit
import app.paperhands.storage.Storage
import cats.effect._
import cats.implicits._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import monocle.macros.syntax.all._

object RedditScraper extends Reddit {
  import Market.Market

  val imgPattern = "^.*\\.(png|jpg|jpeg|gif)$".r
  val urlPattern =
    "(?:https?:\\/\\/)(?:\\w+(?:-\\w+)*\\.)+\\w+(?:-\\w+)*\\S*?(?=[\\s)]|$)".r

  def runOcrAndSaveResult(
      xa: HikariTransactor[IO],
      url: String
  ): IO[String] =
    for {
      cfg <- Config.cfg
      out <- OCR
        .processURL(cfg, url)
        .handleErrorWith(e =>
          for {
            _ <- logger.error(s"Error processing $url with OCR: $e")
          } yield ""
        )
      _ <- Storage.saveOcrCache(model.OcrCache(url, out)).transact(xa)
    } yield out

  def processURL(xa: HikariTransactor[IO], url: String): IO[String] =
    for {
      ocrCache <- Storage.getOcrCache(url).transact(xa)
      out <- ocrCache match {
        case Some(cache) => IO.pure(cache.output)
        case None        => runOcrAndSaveResult(xa, url)
      }
    } yield s"\n$url:\n$out"

  def processURLs(xa: HikariTransactor[IO], urls: List[String]): IO[String] =
    for {
      url <- urls.traverse(processURL(xa, _))
    } yield url.mkString("")

  def isImageURL(url: String): Boolean =
    imgPattern.matches(url)

  def extractImageURLs(body: String): List[String] =
    urlPattern
      .findAllIn(body)
      .toList
      .filter(isImageURL)

  def collectAllImageUrls(e: Entry): List[String] =
    e.url.filter(isImageURL).toList ++ extractImageURLs(e.body)

  def processEntry(xa: HikariTransactor[IO], e: Entry): IO[Unit] =
    for {
      out <- processURLs(xa, collectAllImageUrls(e))
      market <- Market.market
      _ <- process(xa, cfg, market, e.focus(_.body).modify(v => s"$v$out"))
    } yield ()

  def handleEntry(xa: HikariTransactor[IO], e: Entry): IO[Unit] =
    Storage
      .contentExists(e.name)
      .transact(xa)
      .ifM(IO.unit, processEntry(xa, e))

  def sentTestFn(body: String, coll: List[String]): Boolean =
    coll.find(s => body.contains(s)).isDefined

  def getSentimentValue(cfg: Config, body: String): model.SentimentValue =
    (
      sentTestFn(body, cfg.sentiment.bull),
      sentTestFn(body, cfg.sentiment.bear)
    ) match {
      case (true, true)   => model.Bull()
      case (true, false)  => model.Bull()
      case (false, true)  => model.Bear()
      case (false, false) => model.Unknown()
    }

  def getSymbols(market: Market, body: String): List[String] =
    market
      .filter(e => {
        val s = e.symbol
        val exceptionRe = s"(?is).*\\s+$s\\b.*".r
        val normalRe = s"(?is).*\\s*\\$$$s\\b.*".r
        val desperationRe = s"(?s).*\\b+$s\\b.*".r
        // Logic here is if its an exception symbol just match for GME/gme
        // otherwise try to match for $GME or $gme
        // if this did not work make sure its not an exception
        // and length is greater than 1
        // then match just GME but with \b as word bounds market
        // and case sensitive.
        // \b does not work at the end or beginning of string
        (e.isException && exceptionRe.matches(body)) ||
        (normalRe.matches(body)) ||
        (!e.isIgnored &&
          s.length() > 1 &&
          desperationRe.matches(body))
      })
      .map(_.symbol)

  def sentimentFor(
      entry: Entry,
      symbols: List[String],
      sentiment: model.SentimentValue
  ): List[model.Sentiment] =
    model.Sentiment.fromSymbols(
      symbols,
      sentiment,
      entry.name,
      entry.created_time
    )

  def engagementFor(
      cfg: Config,
      entry: Entry,
      symbols: List[String]
  ): List[model.Engagement] =
    model.Engagement.fromSymbols(
      symbols.distinct.filter(!Market.isIgnored(cfg, _)),
      entry.name,
      entry.created_time
    )

  def symbolsFromTree(tree: List[model.ContentMeta]): List[String] =
    tree.map(_.symbols).flatten.distinct

  def extractTreeSymbols(
      xa: HikariTransactor[IO],
      id: Option[String]
  ): IO[List[String]] =
    id match {
      case Some(id) =>
        for {
          tree <- Storage.getParsedTree(id).transact(xa)
        } yield symbolsFromTree(tree)
      case None => IO.pure(List())
    }

  def process(
      xa: HikariTransactor[IO],
      cfg: Config,
      market: Market,
      entry: Entry
  ): IO[Unit] = {
    val symbols = getSymbols(market, entry.body)
    val sentimentVal = getSentimentValue(cfg, entry.body)
    val sentiments = sentimentFor(entry, symbols, sentimentVal)
    val content =
      model.Content.fromRedditEntry(entry, symbols, sentimentVal)

    for {
      treeSymbols <- extractTreeSymbols(xa, entry.parent_id)
      engagements <- IO.pure(
        engagementFor(cfg, entry, symbols ++ treeSymbols)
      )
      _ <- logger.debug(
        s"${entry.author}: ${entry.body} | ${sentiments
          .map(_.symbol)
          .mkString(" ")} <--> ${engagements.map(_.symbol).mkString(" ")} |"
      )
      _ <- (Storage.saveContent(content)
        >> Storage.saveSentiments(sentiments)
        >> Storage.saveEngagements(engagements))
        .transact(xa)
    } yield ()
  }
}

object Scraper {
  def run(xa: HikariTransactor[IO]): IO[ExitCode] =
    for {
      cfg <- Config.cfg
      _ <- RedditScraper
        .loop(xa, cfg.reddit.secret, cfg.reddit.username)
    } yield ExitCode.Success
}
