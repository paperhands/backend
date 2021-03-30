package app.paperhands.yahoo

import cats._
import cats.effect._
import cats.implicits._

import org.http4s._
import org.http4s.implicits._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.MediaType
import org.http4s.Method._

import app.paperhands.model
import app.paperhands.config.Cfg
import app.paperhands.io.{Logger, HttpBackend}

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._

case class YahooResponse(
    price: Double
)

object Yahoo extends HttpBackend with Cfg {
  val logger = Logger("yahoo")

  val ua = "Mozilla/5.0 (Android 4.4; Mobile; rv:41.0) Gecko/41.0 Firefox/41.0"

  def mapTimeFrame(timeFrame: String) =
    timeFrame match {
      case "1day" => "TIME_SERIES_INTRADAY"
      case _      => "POOP"
    }

  def constructUri(
      symbol: String
  ) =
    uri"https://finance.yahoo.com/quote" / symbol

  def constructRequest(
      uri: Uri
  ) =
    GET(
      uri,
      Accept(MediaType.text.html),
      `User-Agent`(ProductId(ua))
    )

  val priceRe = "([0-9]+\\.[0-9]+)".r
  def scrapeHTML(body: String): IO[YahooResponse] = {
    val browser = JsoupBrowser()
    val doc = browser.parseString(body)
    val txt = doc >> text("#quote-header-info")
    val price =
      priceRe
        .findFirstIn(txt)
        .flatMap(_.toDoubleOption)
        .getOrElse(0.0)

    IO(YahooResponse(price))
  }

  def scrape(symbol: String): IO[YahooResponse] = {
    val uri = constructUri(symbol)
    val request = constructRequest(uri)

    client.use { client =>
      client.expect[String](request) >>= scrapeHTML
    }
  }

}
