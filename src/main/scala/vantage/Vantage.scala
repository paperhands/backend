package app.paperhands.vantage

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

import org.http4s._
import org.http4s.implicits._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.MediaType
import org.http4s.Method._
import org.http4s.circe._

import cats._
import cats.effect._
import cats.implicits._

import app.paperhands.model
import app.paperhands.config.Cfg
import app.paperhands.io.{Logger, HttpBackend}
import app.paperhands.format.Parse

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
          Parse.fromFullDateTime(k, meta.timeZone)
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
    open: Double,
    high: Double,
    low: Double,
    close: Double,
    volume: Double
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
    uri"https://www.alphavantage.co/query"
      .withQueryParams(
        Map(
          "function" -> mapTimeFrame(timeFrame),
          "symbol" -> symbol,
          "interval" -> "15min",
          "apikey" -> apiKey,
          "datatype" -> "json",
          "outputsize" -> "full"
        )
      )

  def constructRequest(
      uri: Uri
  ) =
    GET(
      uri,
      Accept(MediaType.application.json),
      `User-Agent`(ProductId(ua))
    )

  def decodeResponseBody(uri: Uri, body: String): IO[VantageResponse] =
    decode[VantageResponse](body) match {
      case Right(r) => IO.pure(r)
      case Left(e) => {
        logger
          .error(
            s"could not parse vantage response: $e from $uri, body:\n$body"
          )
          .as(VantageResponse(VantageMeta("", "", "", "", "", ""), Map()))
      }
    }

  def priceData(symbol: String, period: String): IO[List[model.TimeSeries]] = {
    val uri = constructUri(symbol, period)
    val request = constructRequest(uri)

    client.use { client =>
      client
        .expect(request)(jsonOf[IO, VantageResponse])
        .map(_.toTimeSeries(symbol))
    }
  }
}
