package app.paperhands.market

import scala.io.Source

case class Ticket(
    symbol: String,
    desc: String
)

trait Market {
  val market = Market.load
}

object Market {
  val files = List("custom.txt", "nasdaqlisted.txt", "otherlisted.txt")
  val cleanRe = "\\s*-.*".r

  def cleanupDescription(desc: String) =
    cleanRe.replaceAllIn(desc, "")

  def load: List[Ticket] = {
    files
      .map(f => Source.fromResource(s"data/$f").getLines())
      .flatten
      .map(_.split("\\|"))
      .filter(_.length >= 2)
      .filter(_(0) != "Symbol")
      .map(l => Ticket(l(0), cleanupDescription(l(1))))
  }
}
