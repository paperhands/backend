package app.paperhands.reddit

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import sttp.model.StatusCodes
import com.typesafe.scalalogging.Logger
import java.util.{Calendar, Date}
import java.time.Instant
import sttp.client3._
import sttp.client3.http4s._

import cats._
import cats.effect._
import cats.implicits._

import scala.concurrent._
import scala.concurrent.duration._

object Endpoint extends Enumeration {
  type Endpoint = Value
  val Posts, Comments = Value
}

import Endpoint._

case class LoopState(
    beforePost: String,
    beforeComment: String,
    index: Int
)

trait Reddit {
  implicit val timer = IO.timer(ExecutionContext.global)
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  val blocker: cats.effect.Blocker =
    Blocker.liftExecutionContext(ExecutionContext.global)
  val backend =
    Blocker[IO].flatMap(Http4sBackend.usingDefaultBlazeClientBuilder[IO](_))

  val logger = Logger("reddit")

  def handleEntry(entry: Entry): IO[Unit]

  def loadItems(
      endpoint: Endpoint,
      secret: String,
      state: LoopState
  ): IO[Either[Error, List[Entry]]] = {
    val before = endpoint match {
      case Posts    => state.beforePost
      case Comments => state.beforeComment
    }

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

    backend.use { implicit backend =>
      for {
        _ <- IO(println("in backend use"))
        response <- request.send(backend)
        body <- IO(response.body.getOrElse(""))
        _ <- IO(
          if (!response.code.isSuccess)
            logger.error(s"Received ${response.code} from $url")
        )
        result <- IO(decode[RedditListing](body).map(Entry.fromListing(_)))
      } yield (result)
    }
  }

  def updateState(
      items: Either[Error, List[Entry]],
      state: LoopState,
      patternSize: Int
  ): LoopState = {
    val newState = items match {
      case Right(items) =>
        items.headOption.map(e => (e.kind, e.name)) match {
          case Some(("t1", name)) =>
            state.copy(beforeComment = name)
          case Some(("t3", name)) =>
            state.copy(beforePost = name)
          case _ => state
        }
      case Left(e) => {
        logger.error(s"Error parsing data: $e")
        state
      }
    }

    val ni = state.index + 1
    val newIndex = if (ni >= patternSize) 0 else ni

    newState.copy(index = newIndex)
  }

  def handleItems(
      items: Either[Error, List[Entry]],
      state: LoopState
  ): IO[List[Unit]] = {
    items match {
      case Right(items) =>
        items.traverse(entry => {
          handleEntry(entry)
        })
      case Left(e) => {
        logger.error(s"Error parsing data: $e")
        IO(List())
      }
    }
  }

  def loop(secret: String): IO[Unit] = {
    val pattern = (1 to 10).map(_ => Comments) ++ List(Posts)
    val emptyState = LoopState("", "", 0)

    // TODO this can be split into to separate loops with simpler state,
    // post loop can be called every 30 seconds
    // while comments every 2 seconds
    val io =
      emptyState.iterateForeverM { state =>
        {
          val endpoint = pattern(state.index)

          for {
            items <- loadItems(endpoint, secret, state)
            fibre <- handleItems(items, state).start
            _ <- IO.sleep(2.seconds)
            _ <- fibre.join
          } yield (updateState(items, state, pattern.length))
        }
      }

    for {
      _ <- io
    } yield ()
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
