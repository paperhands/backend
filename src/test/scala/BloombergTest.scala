import minitest._
import app.paperhands.vantage.Vantage
import scala.concurrent.duration._
import cats.implicits._
import cats.effect.IO
import cats.effect.testing.minitest.{IOTestSuite, DeterministicIOTestSuite}

object VantageTestSuite extends IOTestSuite {
  override val timeout = 10.second

  test("get gme price") {
    for {
      output <- Vantage.priceData("GME:US", "1_DAY")
    } yield (assertEquals(output, List()))
  }
}
