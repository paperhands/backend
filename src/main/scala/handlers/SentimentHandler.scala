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
import app.paperhands.market.Market
import app.paperhands.storage.{Storage}
import app.paperhands.io.AddContextShift
import app.paperhands.vantage.Vantage

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
    change_perc: Float,
    pos: Int,
    old_pos: Int
)

object QuoteTrending extends Market {
  def findDesc(symb: String) =
    market.find(_.symbol == symb).map(_.desc)

  def oldPos(symb: String, list: List[model.Trending]) =
    list.indexWhere(_.symbol == symb)

  def changePerc(
      symb: String,
      popularity: Float,
      list: List[model.Trending]
  ): Float = {
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
        findDesc(t.symbol),
        changePerc(t.symbol, t.popularity, previous),
        index,
        oldPos(t.symbol, previous)
      )
    }
}

case class QuoteDetails(
    mentions: ChartResponse,
    engagements: ChartResponse,
    sentiments: ChartResponse,
    price: ChartResponse
)

object QuoteDetails {
  def fromTimeSeries(
      mentions: List[model.TimeSeries],
      engagements: List[model.TimeSeries],
      sentiments: List[model.TimeSeries],
      price: List[model.TimeSeries]
  ) =
    QuoteDetails(
      Chart.fromTimeSeries(mentions),
      Chart.fromTimeSeries(engagements),
      Chart.fromTimeSeries(sentiments),
      Chart.fromTimeSeries(price)
    )
}

trait Encoders {
  implicit val QuoteTrendingsEncoder: EntityEncoder[IO, List[QuoteTrending]] =
    jsonEncoderOf[IO, List[QuoteTrending]]
  implicit val QuoteDetailsEncoder: EntityEncoder[IO, QuoteDetails] =
    jsonEncoderOf[IO, QuoteDetails]
}

object Handler extends Encoders with AddContextShift {
  val logger = Logger("sentiment-handler")

  def toInstant(in: LocalDateTime) =
    in.atZone(ZoneId.systemDefault).toInstant

  def getQuoteTrending(xa: HikariTransactor[IO]): IO[List[QuoteTrending]] = {
    val now = LocalDateTime.now()
    val dayAgo = now.minusDays(1)

    val start = toInstant(dayAgo)
    val end = toInstant(now)

    val prevStart = toInstant(dayAgo.minusDays(1))
    val prevEnd = toInstant(now.minusDays(1))

    for {
      previous <- Storage
        .getTrending(prevStart, prevEnd, 30)
        .transact(xa)
      present <- Storage
        .getTrending(start, end, 30)
        .transact(xa)
    } yield (QuoteTrending.fromTrending(previous, present).take(10))
  }

  def getDetails(
      xa: HikariTransactor[IO],
      symbol: String,
      period: String
  ): IO[QuoteDetails] = {
    val now = LocalDateTime.now()
    val dayAgo = now.minusDays(1)

    val start = toInstant(dayAgo)
    val end = toInstant(now)

    val bucket = "15 minutes"

    for {
      mentions <- Storage
        .getMentionTimeseries(symbol, bucket, start, end)
        .transact(xa)
      engagements <- Storage
        .getEngagementTimeseries(symbol, bucket, start, end)
        .transact(xa)
      sentiments <- Storage
        .getSentimentTimeseries(symbol, bucket, start, end)
        .transact(xa)
      price <- Vantage.priceData(symbol, period)
    } yield (QuoteDetails
      .fromTimeSeries(mentions, engagements, sentiments, price))
  }

  def paperhandsService(xa: HikariTransactor[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "quote" / "trending" =>
      Ok(getQuoteTrending(xa))
    case GET -> Root / "quote" / "details" / symbol / period =>
      Ok(getDetails(xa, symbol.toUpperCase, period))
  }
}
