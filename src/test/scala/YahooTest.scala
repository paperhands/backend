import app.paperhands.yahoo.Yahoo
import scala.concurrent.duration._
import cats.implicits._
import cats.effect.testing.minitest.IOTestSuite

object YahooTestSuite extends IOTestSuite {
  override val timeout = 10.second

  test("get GME price") {
    for {
      output <- Yahoo.scrape("GME")
    } yield assert(output.price > 1.0)
  }
}
