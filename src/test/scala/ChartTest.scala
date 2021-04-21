import minitest._
import app.paperhands.chart.Chart

object ChartTestSuite extends SimpleTestSuite {
  test("Silly dummy test") {
    val data = Chart.fromTimeSeries(List())

    assertEquals(0, data.data.length)
    assertEquals(0, data.titles.length)
  }
}
