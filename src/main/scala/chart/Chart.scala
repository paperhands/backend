package app.paperhands.chart

import app.paperhands.model
import app.paperhands.format.Format

case class Spot(x: Int, y: Int)

case class ChartResponse(
    spots: List[Spot],
    min_y: Int,
    min_x: Int,
    max_y: Int,
    max_x: Int,
    y_titles: Map[Int, String],
    x_titles: Map[Int, String]
)

object Chart {
  def includeLabel(i: Int, limit: Int, max: Int): Boolean =
    0.to(max - 1).by(max / limit).contains(i)

  def getYLabels(min: Int, limit: Int, max: Int) = {
    val total = max - min
    val step = total / limit

    min
      .to(max)
      .by(step)
      .map(i => {
        val v = i + min
        v -> v.toString
      })
      .toMap
  }

  def fromTimeSeries(input: List[model.TimeSeries]): ChartResponse = {
    val maxX = input.length
    val minX = 0

    val maxY = input.map(_.value).maxOption.getOrElse(1000)
    val minY = input.map(_.value).minOption.getOrElse(0)

    val yTitles = getYLabels(minY, 5, maxY)

    val spots = input.zipWithIndex.map { case (v, index) =>
      Spot(index, v.value)
    }

    val xTitles = input.zipWithIndex
      .filter { case (v, index) =>
        includeLabel(index, 5, input.length)
      }
      .map { case (v, index) =>
        index -> Format.HHmm(v.time)
      }
      .toMap

    ChartResponse(
      spots,
      minY,
      minX,
      maxY,
      maxX,
      yTitles,
      xTitles
    )
  }
}
