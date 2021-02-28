package app.paperhands.config

import scala.io.Source
import cats.syntax.either._
import io.circe._
import io.circe.generic.auto._
import io.circe.yaml

case class Config(
    reddit: Reddit,
    repository: Repository,
    http: Http,
    sentiment: Sentiment,
    market: Market
)
case class Reddit(
    secret: String,
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
  val cfg = Config.load
}

object Config {
  def load: Config = {
    val env = sys.env.getOrElse("PAPERHANDS_ENV", "development")
    val path = s"config/$env.yml"

    val json = yaml.parser.parse(
      Source.fromResource(path).mkString
    )

    val config = json
      .leftMap(err => err: Error)
      .flatMap(_.as[Config])
      .valueOr(throw _)

    config
  }
}
