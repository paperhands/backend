package app.paperhands.reddit

import sttp.client3._
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import sttp.model.StatusCodes
import com.typesafe.scalalogging.Logger
import java.util.{Calendar, Date}
import java.time.Instant

object Endpoint extends Enumeration {
  type Endpoint = Value
  val Posts, Comments = Value
}

import Endpoint._

case class LoopState(
    beforePost: String,
    beforeComment: String,
    cache: Map[String, Boolean]
)

trait Reddit {
  val logger = Logger("reddit")

  def handleEntry(entry: Entry)

  def newItems(
      endpoint: Endpoint,
      secret: String,
      before: String = ""
  ): Either[Error, List[Entry]] = {
    val limit = 100
    val ts = System.currentTimeMillis
    val ua = s"linux:$secret:0.0.1-$ts (by u/coderats)"
    val url =
      endpoint match {
        case Posts =>
          uri"https://www.reddit.com/r/wallstreetbets/new.json?before=$before&limit=$limit&raw_json=1"
        case Comments =>
          uri"https://www.reddit.com/r/wallstreetbets/comments.json?before=$before&limit=$limit&raw_json=1"
      }

    val request = basicRequest
      .header("User-Agent", ua)
      .contentType("application/json")
      .get(url)

    val backend = HttpURLConnectionBackend()
    val response = request.send(backend)
    val body = response.body.getOrElse("")

    if (!response.code.isSuccess)
      logger.error(s"Received ${response.code} from $url")

    decode[RedditListing](body).map(Entry.fromListing(_))
  }

  def loop(secret: String) = {
    val pattern = (1 to 10).map(_ => Comments) ++ List(Posts)
    val emptyState = LoopState("", "", Map())

    Stream
      .continually(pattern)
      .flatten
      .foldLeft(emptyState)((streamState, endpoint) => {
        val before = endpoint match {
          case Posts    => streamState.beforePost
          case Comments => streamState.beforeComment
        }

        logger.debug(
          s"Requesting data from $endpoint with $before as last seen item"
        )

        val state = newItems(endpoint, secret, before) match {
          case Right(items) =>
            val state = items.foldLeft(streamState)((state, entry) => {
              if (state.cache.get(entry.name).isEmpty)
                handleEntry(entry)

              state.copy(
                cache = state.cache + (entry.name -> true)
              )
            })

            items.headOption.map(e => (e.kind, e.name)) match {
              case Some(("t1", name)) =>
                state.copy(beforeComment = name)
              case Some(("t3", name)) =>
                state.copy(beforePost = name)
              case _ => state
            }
          case Left(e) => {
            logger.error(s"Error parsing data: $e")
            streamState
          }
        }

        Thread.sleep(1000 * 1)

        state
      })
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
    author: String,
    created_utc: Option[Long]
) extends RedditJsonCodec

case class Entry(
    kind: String,
    id: String,
    name: String,
    author: String,
    permalink: String,
    body: String,
    parent_id: Option[String],
    created_time: Instant,
    url: Option[String]
)

object Entry {
  def getTime(entry: RedditEntryData): Instant = {
    entry.created_utc
      .map((t: Long) => new Date(t * 1000L))
      .getOrElse(Calendar.getInstance.getTime)
      .toInstant
  }

  def bodyFromRedditEntry(entry: RedditEntry): String =
    entry.kind match {
      case "t1" => s"${entry.data.body.getOrElse("")}"
      case "t3" =>
        s"${entry.data.title.map(v => s"$v: ").getOrElse("")}${entry.data.selftext.getOrElse("")}"
    }

  def fromListing(listing: RedditListing): List[Entry] =
    listing.data.children.map(entry =>
      Entry(
        entry.kind,
        entry.data.id,
        entry.data.name,
        entry.data.author,
        entry.data.permalink,
        bodyFromRedditEntry(entry),
        entry.data.parent_id,
        getTime(entry.data),
        entry.data.url
      )
    )
}
