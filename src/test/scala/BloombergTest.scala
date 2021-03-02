import minitest._
import app.paperhands.bloomberg.Bloomberg
import scala.concurrent.duration._
import cats.implicits._
import cats.effect.IO
import cats.effect.testing.minitest.{IOTestSuite, DeterministicIOTestSuite}

object BloombergTestSuite extends IOTestSuite {
  override val timeout = 10.second

  test("get gme price") {
    for {
      output <- Bloomberg.priceData("GME:US", "1_DAY")
    } yield (assertEquals(output, List()))
  }
}
