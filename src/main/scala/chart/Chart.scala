package app.paperhands.chart

import app.paperhands.model

case class ChartResponse()

object Chart {
  def fromTimeSeries(input: List[model.TimeSeries]): ChartResponse =
    ChartResponse()
}
