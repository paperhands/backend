package app.paperhands.market

import scala.io.Source

import cats._
import cats.effect._
import cats.implicits._
import cats.syntax._

import app.paperhands.io.Logger

case class Ticket(
    symbol: String,
    desc: String
)

trait Market {
  val market: List[Ticket] = Market.market
}

object Market {
  val logger = Logger("market-data")

  val files = List("custom.txt", "nasdaqlisted.txt", "otherlisted.txt")
  val cleanRe = "(?i)(?<=(inc|corp)\\.).*".r
  val afterCleanupRe = "(?i) (class [a-z]|common stock).*".r

  def cleanupDescription(desc: String) =
    List(cleanRe, afterCleanupRe).foldLeft(desc) { case (desc, re) =>
      re.replaceAllIn(desc, "")
    }

  def processLines(lines: List[String]): List[Ticket] =
    lines
      .map(_.split("\\|"))
      .filter(_.length >= 2)
      .filter(_(0) != "Symbol")
      .map(l => Ticket(l(0), cleanupDescription(l(1))))

  def readFile(f: String) =
    for {
      _ <- logger.info(s"reading market data from $f")
    } yield (Source.fromResource(s"data/$f").getLines())

  def load: IO[List[Ticket]] =
    files
      .traverse(readFile)
      .map(_.flatten)
      .map(processLines)

  val market = load.unsafeRunSync
}
