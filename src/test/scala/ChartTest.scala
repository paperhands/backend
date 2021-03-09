import minitest._
import app.paperhands.chart.Chart

object ChartTestSuite extends SimpleTestSuite {
  test("includeLabel fn 20 items") {
    val labels = 0
      .to(20)
      .filter(Chart.includeLabel(_, 5, 20))

    assertEquals(labels, Vector(0, 4, 8, 12, 16))
  }

  test("includeLabel fn 120 items") {
    val labels = 0
      .to(120)
      .filter(Chart.includeLabel(_, 5, 120))

    assertEquals(labels, Vector(0, 24, 48, 72, 96))
  }

  test("includeLabel fn 900 items") {
    val labels = 0
      .to(900)
      .filter(Chart.includeLabel(_, 5, 900))

    assertEquals(labels, Vector(0, 180, 360, 540, 720))
  }
}
