package app.paperhands.handlers.paperhands

import app.paperhands.chart._
import app.paperhands.config.Config
import app.paperhands.io.Logger
import app.paperhands.market.Market
import app.paperhands.model
import app.paperhands.storage.Storage
import app.paperhands.yahoo._
import cats.effect._
import cats.implicits._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import io.circe.generic.auto._
import io.circe.literal._
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

case class QuoteTrending(
    symbol: String,
    desc: Option[String],
    change_perc: Double,
    pos: Int,
    old_pos: Int,
    popularity: Int
)

object Desc {
  import Market.Market

  def find(market: Market, symb: String) =
    market.find(_.symbol == symb).map(_.desc)
}

object QuoteTrending {
  import Market.Market

  def oldPos(symb: String, list: List[model.Trending]) =
    list.indexWhere(_.symbol == symb)

  def changePerc(
      symb: String,
      popularity: Double,
      list: List[model.Trending]
  ): Double = {
    val oldPopularity = list
      .find(_.symbol == symb)
      .map(_.popularity)
      .getOrElse(popularity)

    Math.round((popularity - oldPopularity) / oldPopularity * 10000) / 100
  }

  def fromTrending(
      market: Market,
      previous: List[model.Trending],
      present: List[model.Trending]
  ): List[QuoteTrending] =
    present.zipWithIndex.map { case (t, index) =>
      QuoteTrending(
        t.symbol,
        Desc.find(market, t.symbol),
        changePerc(t.symbol, t.popularity, previous),
        index,
        oldPos(t.symbol, previous),
        t.popularity.toInt
      )
    }
}

case class QuoteDetails(
    symbol: String,
    desc: Option[String],
    current_price: Double,
    mentions: ChartResponse,
    engagements: ChartResponse,
    sentiments: ChartResponse,
    price: ChartResponse,
    popularity: model.Popularity
)

object QuoteDetails {
  import Market.Market

  def fromQueryResults(
      market: Market,
      symbol: String,
      yahooResponse: YahooResponse,
      price: List[model.TimeSeries],
      dbData: (
          List[model.TimeSeries],
          List[model.TimeSeries],
          List[model.TimeSeries],
          model.Popularity
      )
  ) = {
    val (mentions, engagements, sentiments, popularity) = dbData

    QuoteDetails(
      symbol,
      Desc.find(market, symbol),
      yahooResponse.price,
      Chart.fromTimeSeries(mentions),
      Chart.fromTimeSeries(engagements),
      Chart.fromTimeSeries(sentiments),
      Chart.fromTimeSeries(price),
      popularity
    )
  }
}

case class QuoteSearchResult(symbol: String, desc: String)

object SearchQuote {
  import Market.Market

  val descSearchLimit = 20
  val overallLimit = 50
  val descRatioCutoff = 70

  def findBySymbol(market: Market, term: String) = {
    val lowerTerm = term.toLowerCase

    market
      .filter(t => t.symbol.toLowerCase.contains(lowerTerm))
  }

  def findByDesc(market: Market, term: String) = {
    val lowerTerm = term.toLowerCase

    market
      .map { t =>
        val ratio = FuzzySearch.partialRatio(lowerTerm, t.desc.toLowerCase)

        (ratio, t)
      }
      .filter(_._1 >= descRatioCutoff)
      .sortBy(_._1)
      .reverse
      .take(descSearchLimit)
      .map(_._2)
  }

  def find(market: Market, term: String) =
    (findBySymbol(market, term) ++ findByDesc(market, term))
      .map(t => QuoteSearchResult(t.symbol, t.desc))
      .distinct
      .take(overallLimit)

}

trait Encoders {
  implicit val QuoteTrendingsEncoder: EntityEncoder[IO, List[QuoteTrending]] =
    jsonEncoderOf[IO, List[QuoteTrending]]
  implicit val QuoteDetailsEncoder: EntityEncoder[IO, QuoteDetails] =
    jsonEncoderOf[IO, QuoteDetails]
  implicit val ContentListEncoder: EntityEncoder[IO, List[model.Content]] =
    jsonEncoderOf[IO, List[model.Content]]
  implicit val QuoteListEncoder: EntityEncoder[IO, List[QuoteSearchResult]] =
    jsonEncoderOf[IO, List[QuoteSearchResult]]
  implicit val IntEncoder: EntityEncoder[IO, Int] =
    jsonEncoderOf[IO, Int]
}

class Handler(xa: HikariTransactor[IO], cfg: Config, market: Market.Market)
    extends Encoders {
  val logger = Logger("sentiment-handler")

  def toInstant(in: LocalDateTime) =
    in.atZone(ZoneId.of("UTC")).toInstant

  def periodToDays(period: String): Int =
    period match {
      case "1D" => 1
      case "5D" => 5
      case "1W" => 7
      case "1M" => 30
      case "6M" => 180
      case "1Y" => 360
      case _    => 1
    }

  def periodToBucket(period: String): String =
    period match {
      case "1D" => "15 minutes"
      case "5D" => "12 hours"
      case "1W" => "12 hours"
      case "1M" => "1 day"
      case "6M" => "1 week"
      case "1Y" => "2 weeks"
      case _    => "15 minutes"
    }

  def fetchTrending(
      xa: HikariTransactor[IO],
      period: String
  ): IO[List[QuoteTrending]] = {
    val days = periodToDays(period)
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val dayAgo = now.minusDays(days)

    val start = toInstant(dayAgo)
    val end = toInstant(now)

    val prevStart = toInstant(dayAgo.minusDays(days))
    val prevEnd = toInstant(now.minusDays(days))

    for {
      previous <- Storage
        .getTrending(prevStart, prevEnd, 50)
        .transact(xa)
      present <- Storage
        .getTrending(start, end, 50)
        .transact(xa)
    } yield QuoteTrending.fromTrending(market, previous, present).take(20)
  }

  def fetchDBDataForDetails(
      symbol: String,
      bucket: String,
      start: Instant,
      end: Instant
  ): ConnectionIO[
    (
        List[model.TimeSeries],
        List[model.TimeSeries],
        List[model.TimeSeries],
        model.Popularity
    )
  ] =
    (
      Storage
        .getMentionTimeseries(symbol, bucket, start, end),
      Storage
        .getEngagementTimeseries(symbol, bucket, start, end),
      Storage
        .getSentimentTimeseries(symbol, bucket, start, end),
      Storage
        .getPopularityForInterval(symbol, start, end)
    ).tupled

  def fetchDetails(
      xa: HikariTransactor[IO],
      symbol: String,
      period: String
  ): IO[QuoteDetails] = {
    val days = periodToDays(period)
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val dayAgo = now.minusDays(days)
    val start = toInstant(dayAgo)
    val end = toInstant(now)

    val bucket = periodToBucket(period)

    for {
      market <- Market.market
      yahooResponse <- Yahoo.scrape(symbol)
      dbData <- fetchDBDataForDetails(
        symbol,
        bucket,
        start,
        end
      ).transact(xa)
    } yield QuoteDetails
      .fromQueryResults(
        market,
        symbol,
        yahooResponse,
        List(),
        dbData
      )
  }

  def getSampleContent(
      xa: HikariTransactor[IO],
      symbol: String
  ): IO[List[model.Content]] =
    Storage.getSamples(symbol, 10).transact(xa)

  def getUnlabeledContent(
      xa: HikariTransactor[IO],
      limit: Int
  ): IO[List[model.Content]] =
    Storage.getUnlabeledContent(limit).transact(xa)

  def findQuotes(term: String): IO[List[QuoteSearchResult]] =
    IO.pure(SearchQuote.find(market, term))

  def labelContent(
      contentID: String,
      label: Int
  ): IO[Int] =
    Storage.createLabel(contentID, label).transact(xa)

  def paperhandsService = HttpRoutes.of[IO] {
    case GET -> Root / "quote" / "search" / term =>
      Ok(findQuotes(term))
    case GET -> Root / "quote" / "trending" / period =>
      Ok(fetchTrending(xa, period))
    case GET -> Root / "quote" / "details" / symbol / period =>
      Ok(fetchDetails(xa, symbol.toUpperCase, period))
    case GET -> Root / "content" / "samples" / symbol =>
      Ok(getSampleContent(xa, symbol.toUpperCase))
    case GET -> Root / "content" / "unlabeled" =>
      Ok(getUnlabeledContent(xa, 10))
    case GET -> Root / "content" / "unlabeled" / limit =>
      Ok(getUnlabeledContent(xa, limit.toInt))
    case PUT -> Root / "content" / "label" / contentID / label =>
      Ok(labelContent(contentID, label.toInt))
  }
}
