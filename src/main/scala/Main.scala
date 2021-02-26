import sttp.client3._
import io.circe.jawn.decode, io.circe.syntax._
import io.circe.Codec

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

  // response.header(...): Option[String]
  println(response.header("Content-Length"))

  // response.body: by default read into an Either[String, String] to indicate failure or success
  println(response.body)

  val listing = decode[RedditJson](response.body.getOrElse(""))
  listing match {
    case Right(listing) => println(listing)
    case Left(err)      => println(err)
  }
}

sealed trait RedditJson derives Codec.AsObject

case class Listing(kind: String) extends RedditJson
