package app.paperhands.bloomberg

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import sttp.client3._

import cats._
import cats.effect._
import cats.implicits._

import app.paperhands.model
import app.paperhands.io.{Logger, HttpBackend}

case class BloombergResponse() {
  def toTimeSeries: List[model.TimeSeries] =
    List()
}

object Bloomberg extends HttpBackend {
  var logger = Logger("bloomberg-api")

  def constructRequest(
      symbol: String,
      timeFrame: String
  ): IO[Request[Either[String, String], Any with Any]] =
    IO.pure(
      basicRequest
        .get(
          uri"https://www.bloomberg.com/markets/api/bulk-time-series/price/$symbol?timeFrame=$timeFrame"
        )
    )

  def decodeResponseBody(body: String): IO[List[model.TimeSeries]] =
    decode[BloombergResponse](body).map(_.toTimeSeries) match {
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
