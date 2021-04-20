package app.paperhands.chart

import app.paperhands.format.Format
import app.paperhands.model

case class Spot(x: Int, y: Int)

case class ChartResponse(
    data: List[Int],
    titles: List[String]
)

object Chart {
  private def format(v: model.TimeSeries) =
    Format.rfc3339(v.time)

  def fromTimeSeries(input: List[model.TimeSeries]): ChartResponse = {
    val data = input.map(_.value)
    val titles = input.map(format)

    ChartResponse(data, titles)
  }
}
