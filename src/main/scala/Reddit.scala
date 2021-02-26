package app.paperhands.reddit

import sttp.client3._
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import sttp.model.StatusCodes
import com.typesafe.scalalogging.Logger

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

  def handleComment(comment: RedditComment)
  def handlePost(post: RedditPost)

  def newItems(
      endpoint: Endpoint,
      secret: String,
      before: String = ""
  ): Either[Error, List[RedditItem]] = {
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

    decode[RedditListing](body).map(RedditItem.fromListing(_))
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
            val state = items.foldLeft(streamState)((state, item) => {
              val name = item.getName

              if (state.cache.get(name).isEmpty)
                item match {
                  case c: RedditComment =>
                    handleComment(c)
                  case p: RedditPost =>
                    handlePost(p)
                }

              state.copy(
                cache = state.cache + (name -> true)
              )
            })

            items.headOption match {
              case Some(i: RedditComment) =>
                state.copy(beforeComment = i.getName)
              case Some(i: RedditPost) =>
                state.copy(beforePost = i.getName)
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
    author: String
) extends RedditJsonCodec

trait RedditItem {
  def getName: String
}

case class RedditPost(
    kind: String,
    id: String,
    name: String,
    author: String,
    permalink: String,
    title: String,
    body: String,
    url: Option[String]
) extends RedditItem {
  def getName: String = {
    this.name
  }
}
case class RedditComment(
    kind: String,
    id: String,
    name: String,
    author: String,
    permalink: String,
    body: String,
    parent_id: String
) extends RedditItem {
  def getName: String = {
    this.name
  }
}

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
