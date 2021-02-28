package app.paperhands.handlers.paperhands

import cats._
import cats.effect._
import cats.implicits._

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze._
import org.http4s.implicits._

import app.paperhands.model
import app.paperhands.market.Market
import app.paperhands.storage.Storage

import java.util.Calendar
import java.time.LocalDateTime

import doobie.util.meta._
import java.time.ZoneId

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import io.circe.generic.JsonCodec

import app.paperhands.io.Logger

case class QuoteTrending(
    symbol: String,
    desc: String,
    change_perc: Float,
    pos: Int,
    old_pos: Int
)

object QuoteTrending extends Market {
  def fromTrending(input: List[model.Trending]): List[QuoteTrending] =
    input.map(t => QuoteTrending(t.symbol, "", 0, t.popularity, 0))
}

object Handler {
  val logger = Logger("sentiment-handler")

  // implicit def trendingEncoder: EntityEncoder[IO, QuoteTrending] =
  //   Encoder.instance { qt: QuoteTrending =>
  //     qt.asJson.noSpaces
  //   }
  // implicit def trendingsEncoder: EntityEncoder[IO, Seq[QuoteTrending]] =
  //   Encoder.instance { qts: List[QuoteTrending] =>
  //     qts.asJson.noSpaces
  //   }

  def getQuoteTrending: List[QuoteTrending] = {
    // val now = LocalDateTime.now()
    // val dayAgo = now.minusDays(1)

    // val trending =
    //   Storage.getTending(
    //     now.toInstant(ZoneId.systemDefault),
    //     dayAgo.toInstant(ZoneId.systemDefault),
    //     30
    //   )
    // logger.warn(s"trending: $trending")

    // QuoteTrending.fromTrending(trending)
    List()
  }

  val paperhandsService = HttpRoutes.of[IO] {
    case GET -> Root / "quote" / "trending" =>
      Ok(getQuoteTrending.asJson.noSpaces)
    case GET -> Root / "quote" / "details" / symbol / period =>
      Ok(s"details for $symbol with $period")
  }
}
