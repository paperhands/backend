package app.paperhands.vantage

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import sttp.client3._

import cats._
import cats.effect._
import cats.implicits._

import app.paperhands.model
import app.paperhands.config.Cfg
import app.paperhands.io.{Logger, HttpBackend}

case class VantageResponse() {
  def toTimeSeries: List[model.TimeSeries] =
    List()
}

object Vantage extends HttpBackend with Cfg {
  val logger = Logger("bloomberg-api")

  val ua = "Mozilla/5.0 (Android 4.4; Mobile; rv:41.0) Gecko/41.0 Firefox/41.0"
  val apiKey = cfg.vantage.api_key

  def mapTimeFrame(timeFrame: String) =
    timeFrame match {
      case "1day" => "TIME_SERIES_INTRADAY"
      case _      => "POOP"
    }

  def constructRequest(
      symbol: String,
      timeFrame: String
  ): IO[Request[Either[String, String], Any with Any]] =
    IO.pure(
      basicRequest
        .header("User-Agent", ua)
        .contentType("application/json")
        .get(
          uri"https://www.alphavantage.co/query?function=${mapTimeFrame(timeFrame)}&symbol=$symbol&interval=5min&apikey=$apiKey"
        )
    )

  def decodeResponseBody(body: String): IO[List[model.TimeSeries]] =
    decode[VantageResponse](body).map(_.toTimeSeries) match {
      case Right(l) => IO.pure(l)
      case Left(e) => {
        for {
          _ <- logger.error(
            s"could not parse bloomberg response: $e, body:\n$body"
          )
        } yield (List())
      }
    }

  def priceData(symbol: String, period: String): IO[List[model.TimeSeries]] = {
    backend.use { backend =>
      for {
        request <- constructRequest(symbol, period)
        response <- request.send(backend)
        ts <- decodeResponseBody(response.body.getOrElse(""))
      } yield (ts)
    }
  }

}
