package app.paperhands.vantage

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import sttp.client3._
import sttp.model._

import cats._
import cats.effect._
import cats.implicits._

import app.paperhands.model
import app.paperhands.config.Cfg
import app.paperhands.io.{Logger, HttpBackend}

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeParsing {
  def toInstant(in: String, zone: String) = {
    // 2021-03-01 20:00:00
    LocalDateTime
      .parse(
        in,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
      )
      .atZone(ZoneId.of(zone))
      .toInstant
  }
}

case class VantageResponse(
    meta: VantageMeta,
    timeSeries: Map[String, VantageData]
) {
  def toTimeSeries(symbol: String): List[model.TimeSeries] = {
    timeSeries
      .map { case (k, v) =>
        model.TimeSeries(
          symbol,
          (v.open * 100.0).toInt,
          TimeParsing.toInstant(k, meta.timeZone)
        )
      }
      .toList
      .reverse
  }
}

case class VantageMeta(
    information: String,
    symbol: String,
    lastRefreshed: String,
    interval: String,
    outputSize: String,
    timeZone: String
)
case class VantageData(
    open: Float,
    high: Float,
    low: Float,
    close: Float,
    volume: Float
)

trait Decoders {
  implicit val decodeVantageMeta: Decoder[VantageMeta] =
    Decoder.forProduct6(
      "1. Information",
      "2. Symbol",
      "3. Last Refreshed",
      "4. Interval",
      "5. Output Size",
      "6. Time Zone"
    )(
      VantageMeta.apply
    )

  implicit val decodeVantageData: Decoder[VantageData] =
    Decoder.forProduct5(
      "1. open",
      "2. high",
      "3. low",
      "4. close",
      "5. volume"
    )(
      VantageData.apply
    )

  implicit val decodeVantageResponse: Decoder[VantageResponse] =
    Decoder.forProduct2(
      "Meta Data",
      "Time Series (15min)"
    )(
      VantageResponse.apply
    )
}

object Vantage extends HttpBackend with Cfg with Decoders {
  val logger = Logger("vantage-api")

  val ua = "Mozilla/5.0 (Android 4.4; Mobile; rv:41.0) Gecko/41.0 Firefox/41.0"
  val apiKey = cfg.vantage.api_key

  def mapTimeFrame(timeFrame: String) =
    timeFrame match {
      case "1day" => "TIME_SERIES_INTRADAY"
      case _      => "POOP"
    }

  def constructUri(
      symbol: String,
      timeFrame: String
  ) =
    uri"https://www.alphavantage.co/query?function=${mapTimeFrame(timeFrame)}&symbol=$symbol&interval=15min&apikey=$apiKey&datatype=json&outputsize=full"

  def constructRequest(
      uri: Uri
  ) =
    basicRequest
      .header("User-Agent", ua)
      .contentType("application/json")
      .get(uri)

  def decodeResponseBody(uri: Uri, body: String): IO[VantageResponse] =
    decode[VantageResponse](body) match {
      case Right(r) => IO.pure(r)
      case Left(e) => {
        for {
          _ <- logger.error(
            s"could not parse vantage response: $e from $uri, body:\n$body"
          )
        } yield (VantageResponse(VantageMeta("", "", "", "", "", ""), Map()))
      }
    }

  def priceData(symbol: String, period: String): IO[List[model.TimeSeries]] = {
    val uri = constructUri(symbol, period)
    val request = constructRequest(uri)

    backend.use { backend =>
      request.send(backend).map(_.body.getOrElse("")) >>= ((v: String) =>
        decodeResponseBody(uri, v).map(_.toTimeSeries(symbol))
      )
    }
  }

}
