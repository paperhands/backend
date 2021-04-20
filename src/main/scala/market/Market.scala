package app.paperhands.market

import app.paperhands.config.Config
import app.paperhands.io.Logger
import cats.effect._
import cats.implicits._
import fs2._
import fs2.data.csv._

import scala.io.Source

case class Ticket(
    symbol: String,
    desc: String,
    isException: Boolean,
    isIgnored: Boolean
)

object Market {
  type Market = List[Ticket]

  val logger = Logger("market-data")

  val files = List("nasdaqlisted.txt", "otherlisted.txt", "custom.txt")
  val cleanRe = "(?i)(?<=(inc|corp)\\.).*".r
  val afterCleanupRe = "(?i) (class [a-z]|[- ]*common stock).*".r

  def cleanupDescription(desc: String) =
    List(cleanRe, afterCleanupRe).foldLeft(desc) { case (desc, re) =>
      re.replaceAllIn(desc, "")
    }

  def isException(cfg: Config, symb: String): Boolean =
    cfg.market.exceptions.find(_ == symb).isDefined

  def isIgnored(cfg: Config, symb: String): Boolean =
    cfg.market.ignores.find(_ == symb).isDefined

  def parseCsv(cfg: Config)(csv: String): List[Ticket] =
    Stream
      .emit(csv)
      .through(lowlevel.rows[Fallible, String]('|'))
      .map(l => List(l.get(0), l.get(1)).sequence)
      .collect {
        case Some(List(s, d)) if s != "Symbol" =>
          Ticket(
            s,
            cleanupDescription(d),
            isException(cfg, s),
            isIgnored(cfg, s)
          )
      }
      .compile
      .toList
      .getOrElse(List())

  def readFile(cfg: Config)(f: String): IO[List[Ticket]] =
    logger.info(s"reading market data from $f") >>
      IO(Source.fromResource(s"data/$f").mkString) >>= { data =>
      IO.pure(parseCsv(cfg)(data))
    }

  def load(cfg: Config): IO[List[Ticket]] =
    files.traverse(readFile(cfg)).map(_.flatten)

  def market(cfg: Config) = load(cfg)
}
