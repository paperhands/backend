package app.paperhands.market

import scala.io.Source

import cats._
import cats.effect._
import cats.implicits._
import cats.syntax._

import fs2._
import fs2.io.file._
import fs2.data.csv._
import java.nio.file.{Files, Paths}

import app.paperhands.config.Cfg
import app.paperhands.io.Logger

case class Ticket(
    symbol: String,
    desc: String,
    isException: Boolean,
    isIgnored: Boolean
)

trait Market {
  val market: List[Ticket] = Market.market
}

object Market extends Cfg {
  val logger = Logger("market-data")

  val files = List("nasdaqlisted.txt", "otherlisted.txt", "custom.txt")
  val cleanRe = "(?i)(?<=(inc|corp)\\.).*".r
  val afterCleanupRe = "(?i) (class [a-z]|[- ]*common stock).*".r

  def cleanupDescription(desc: String) =
    List(cleanRe, afterCleanupRe).foldLeft(desc) { case (desc, re) =>
      re.replaceAllIn(desc, "")
    }

  def isException(symb: String): Boolean =
    cfg.market.exceptions.find(_ == symb).isDefined

  def isIgnored(symb: String): Boolean =
    cfg.market.ignores.find(_ == symb).isDefined

  def parseCsv(csv: String) =
    Stream
      .emits(csv)
      .through(rows[IO]('|'))
      .map(l => List(l.get(0), l.get(1)).sequence)
      .collect {
        case Some(List(s, d)) if s != "Symbol" =>
          Ticket(s, cleanupDescription(d), isException(s), isIgnored(s))
      }
      .compile
      .toList

  def readFile(f: String) =
    logger.info(s"reading market data from $f") >>
      IO(Source.fromResource(s"data/$f").mkString) >>=
      parseCsv

  def load: IO[List[Ticket]] =
    files.traverse(readFile).map(_.flatten)

  import cats.effect.unsafe.implicits.global
  val market = load.unsafeRunSync
}
