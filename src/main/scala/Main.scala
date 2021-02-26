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
      // uri"https://www.reddit.com/r/wallstreetbets/new.json?before=$before&limit=$limit&raw_json=1"
      uri"https://www.reddit.com/r/wallstreetbets/comments.json?before=$before&limit=$limit&raw_json=1"
    )

  val backend = HttpURLConnectionBackend()
  val response = request.send(backend)

  // response.body: by default read into an Either[String, String] to indicate failure or success

  var body = response.body.getOrElse("")
  println(body)
  val listing = decode[RedditListing](body)
  listing match {
    case Right(listing) => println(RedditItem.fromListing(listing))
    case Left(err)      => println(err)
  }
}

sealed trait RedditJsonCodec

// case class RedditListing(kind: String) extends RedditJsonCodec
case class RedditListing(kind: Option[String], data: RedditListinData)
    extends RedditJsonCodec
case class RedditListinData(dist: Int, children: List[RedditEntry])
    extends RedditJsonCodec
case class RedditEntry(
    kind: String,
    data: RedditEntryData
) extends RedditJsonCodec
case class RedditEntryData(
    id: String,
    name: String,
    parent_id: Option[String],
    permalink: String,
    title: Option[String],
    selftext: Option[String],
    body: Option[String],
    url: Option[String],
    author: String
) extends RedditJsonCodec

trait RedditItem

case class RedditComment(
    kind: String,
    id: String,
    name: String,
    author: String,
    permalink: String,
    body: String,
    parent_id: String
) extends RedditItem
case class RedditPost(
    kind: String,
    id: String,
    name: String,
    author: String,
    permalink: String,
    title: String,
    selftext: String,
    url: Option[String]
) extends RedditItem

object RedditItem {
  def fromListing(listing: RedditListing): List[RedditItem] = {
    listing.data.children.map(entry =>
      entry.kind match {
        case "t3" =>
          RedditPost(
            entry.kind,
            entry.data.id,
            entry.data.name,
            entry.data.author,
            entry.data.permalink,
            entry.data.title.getOrElse(""),
            entry.data.selftext.getOrElse(""),
            entry.data.url
          )
        case "t1" =>
          RedditComment(
            entry.kind,
            entry.data.id,
            entry.data.name,
            entry.data.author,
            entry.data.permalink,
            entry.data.body.getOrElse(""),
            entry.data.parent_id.getOrElse("")
          )
      }
    )
  }
}
