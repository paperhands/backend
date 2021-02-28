package app.paperhands.chart

import app.paperhands.model

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date

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
  def formatTime(t: Instant) = {
    val df = new SimpleDateFormat("HH:mm")
    df.format(Date.from(t))
  }

  def includeLabel(i: Int, limit: Int, totalNum: Int): Boolean =
    i == totalNum - 1 || i == 0 || (0 < i && i % (totalNum / limit) == 0 && i < totalNum)

  def getYLabels(min: Int, limit: Int, max: Int) = {
    val total = max - min

    0.to(total)
      .by(total / limit)
      .map(i => {
        val v = i + min
        v -> v.toString
      })
      .toMap
  }

  def fromTimeSeries(input: List[model.TimeSeries]): ChartResponse = {
    val maxX = input.length
    val minX = 0

    val maxY = input.map(_.value).maxOption.getOrElse(10)
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
        index -> formatTime(v.time)
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
