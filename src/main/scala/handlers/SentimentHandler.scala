package app.paperhands.handlers.paperhands

import cats._
import cats.effect._
import cats.implicits._

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.circe._

import app.paperhands.model
import app.paperhands.chart._
import app.paperhands.popularity.{Popularity, PopularityResponse}
import app.paperhands.market.{Ticket, Market}
import app.paperhands.storage.{Storage}
import app.paperhands.io.AddContextShift
import app.paperhands.vantage.Vantage
import app.paperhands.yahoo._

import java.util.Calendar
import java.time.LocalDateTime

import doobie.util.meta._
import java.time.ZoneId

import io.circe.literal._
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import io.circe.generic.JsonCodec

import app.paperhands.io.Logger

import doobie._
import doobie.implicits._
import doobie.hikari.HikariTransactor

case class QuoteTrending(
    symbol: String,
    desc: Option[String],
    change_perc: Double,
    pos: Int,
    old_pos: Int,
    popularity: Int
)

object Desc extends Market {
  def find(symb: String) =
    market.find(_.symbol == symb).map(_.desc)
}

object QuoteTrending {
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
      previous: List[model.Trending],
      present: List[model.Trending]
  ): List[QuoteTrending] =
    present.zipWithIndex.map { case (t, index) =>
      QuoteTrending(
        t.symbol,
        Desc.find(t.symbol),
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
    popularity: PopularityResponse
)

object QuoteDetails {
  def fromQueryResults(
      symbol: String,
      yahooResponse: YahooResponse,
      mentions: List[model.TimeSeries],
      engagements: List[model.TimeSeries],
      sentiments: List[model.TimeSeries],
      price: List[model.TimeSeries],
      popularity: model.Popularity
  ) =
    QuoteDetails(
      symbol,
      Desc.find(symbol),
      yahooResponse.price,
      Chart.fromTimeSeries(mentions),
      Chart.fromTimeSeries(engagements),
      Chart.fromTimeSeries(sentiments),
      Chart.fromTimeSeries(price),
      Popularity.fromQuery(popularity)
    )
}

case class QuoteSearchResult(symbol: String, desc: String)

object SearchQuote extends Market {
  def findBy(f: Ticket => String)(term: String) =
    market
      .filter(t => f(t).toLowerCase.contains(term.toLowerCase))
      .map(t => QuoteSearchResult(t.symbol, t.desc))

  def find(term: String) =
    (findBy(_.symbol)(term) ++ findBy(_.desc)(term)).distinct

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
}

object Handler extends Encoders with AddContextShift {
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
    } yield QuoteTrending.fromTrending(previous, present).take(20)
  }

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
      // priceFiber <- Vantage.priceData(symbol, period).start
      yahooF <- Yahoo.scrape(symbol).start
      mentions <- Storage
        .getMentionTimeseries(symbol, bucket, start, end)
        .transact(xa)
      engagements <- Storage
        .getEngagementTimeseries(symbol, bucket, start, end)
        .transact(xa)
      sentiments <- Storage
        .getSentimentTimeseries(symbol, bucket, start, end)
        .transact(xa)
      popularity <- Storage
        .getPopularityForInterval(symbol, start, end)
        .transact(xa)
      // price <- priceFiber.join
      price <- IO.pure(List())
      yahooResponse <- yahooF.join
    } yield QuoteDetails
      .fromQueryResults(
        symbol,
        yahooResponse,
        mentions,
        engagements,
        sentiments,
        price,
        popularity
      )
  }

  def getSampleContent(
      xa: HikariTransactor[IO],
      symbol: String
  ): IO[List[model.Content]] =
    Storage.getSamples(symbol, 10).transact(xa)

  def findQuotes(term: String): IO[List[QuoteSearchResult]] =
    IO.pure(SearchQuote.find(term).take(50))

  def paperhandsService(xa: HikariTransactor[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "quote" / "search" / term =>
      Ok(findQuotes(term))
    case GET -> Root / "quote" / "trending" / period =>
      Ok(fetchTrending(xa, period))
    case GET -> Root / "quote" / "details" / symbol / period =>
      Ok(fetchDetails(xa, symbol.toUpperCase, period))
    case GET -> Root / "content" / "sample" / symbol =>
      Ok(getSampleContent(xa, symbol.toUpperCase))
  }
}
