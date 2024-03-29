package app.paperhands.config

import app.paperhands.io.Logger
import cats.effect._
import cats.implicits._
import io.circe.generic.auto._
import io.circe.yaml

import scala.io.Source

case class Config(
    reddit: Reddit,
    repository: Repository,
    http: Http,
    vantage: Vantage,
    tesseract: Tesseract,
    sentiment: Sentiment,
    market: Market
)
case class Reddit(
    secret: String,
    username: String,
    thread_pool: Int
)
case class Repository(
    user: String,
    password: String,
    host: String,
    port: Int,
    database: String,
    max_conns: Int,
    min_conns: Int,
    conn_lifetime: Int
)
case class Http(
    port: Int,
    host: String
)
case class Vantage(
    api_key: String
)
case class Tesseract(
    command: String
)
case class Sentiment(
    bull: List[String],
    bear: List[String]
)
case class Market(
    exceptions: List[String],
    ignores: List[String]
)

object Config {
  val logger = Logger("main")

  def readFile(path: String) =
    logger.info(s"loading config from $path") >>
      IO(Source.fromResource(path).mkString)

  def parseYaml(contents: String) =
    IO(
      yaml.parser
        .parse(contents)
        .flatMap(_.as[Config])
        .valueOr(throw _)
    )

  def load: IO[Config] =
    IO(sys.env.getOrElse("PAPERHANDS_ENV", "development")).map(env =>
      s"config/$env.yml"
    ) >>= readFile >>= parseYaml

  val cfg = load
}
