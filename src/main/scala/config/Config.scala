package app.paperhands.config

import scala.io.Source
import io.circe._
import io.circe.generic.auto._
import io.circe.yaml

import cats._
import cats.effect._
import cats.implicits._
import cats.syntax._

import app.paperhands.io.Logger

case class Config(
    reddit: Reddit,
    repository: Repository,
    http: Http,
    vantage: Vantage,
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
case class Sentiment(
    bull: List[String],
    bear: List[String]
)
case class Market(
    // meta: Map[String, String],
    exceptions: List[String],
    ignores: List[String]
)

trait Cfg {
  val cfg: Config = Config.cfg
}

object Config {
  val logger = Logger("main")

  def readFile(path: String) =
    logger.info(s"loading config from $path") *>
      IO(Source.fromResource(path).mkString)

  def parseYaml(contents: String) =
    IO(
      yaml.parser
        .parse(contents)
        .flatMap(_.as[Config])
        .valueOr(throw _)
    )

  def load: IO[Config] =
    IO(sys.env.getOrElse("PAPERHANDS_ENV", "development")) >>=
      ((env: String) => IO.pure(s"config/$env.yml")) >>=
      readFile >>=
      parseYaml

  val cfg = load.unsafeRunSync
}
