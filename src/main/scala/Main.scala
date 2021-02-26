import sttp.client3._
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

// @main def hello: Unit =
object Main extends App {
  val before: Option[String] = None
  val limit = 100
  val ts = System.currentTimeMillis()
  val ua = s"linux:c1PyjStqy4XJ1w:0.0.1-$ts (by u/coderats)"

  // the `query` parameter is automatically url-encoded
  // `sort` is removed, as the value is not defined
  val request = basicRequest
    .header("User-Agent", ua)
    .contentType("application/json")
    .get(
      uri"https://www.reddit.com/r/wallstreetbets/new.json?before=$before&limit=$limit&raw_json=1"
    )

  val backend = HttpURLConnectionBackend()
  val response = request.send(backend)

  // response.body: by default read into an Either[String, String] to indicate failure or success

  var body = response.body.getOrElse("")
  println(body)
  val listing = decode[RedditJson](body)
  listing match {
    case Right(listing) => println(listing)
    case Left(err)      => println(err)
  }
}

sealed trait RedditJson

// case class RedditListing(kind: String) extends RedditJson
case class RedditListing(kind: String, data: RedditData) extends RedditJson
case class RedditData(dist: Int, children: List[RedditEntry]) extends RedditJson
case class RedditEntry(title: String) extends RedditJson
