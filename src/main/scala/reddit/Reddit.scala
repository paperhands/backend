package app.paperhands.reddit

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import sttp.model.StatusCodes
import java.util.{Calendar, Date}
import java.time.Instant
import sttp.client3._
import sttp.client3.http4s._

import cats._
import cats.effect._
import cats.implicits._

import scala.concurrent._
import scala.concurrent.duration._

import app.paperhands.io.Logger
import app.paperhands.io.AddContextShift

import doobie.hikari.HikariTransactor

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

trait Reddit extends AddContextShift {
  implicit val timer = IO.timer(ExecutionContext.global)
  val backend =
    Blocker[IO].flatMap(Http4sBackend.usingDefaultBlazeClientBuilder[IO](_))

  val logger = Logger("reddit")

  def handleEntry(xa: HikariTransactor[IO], entry: Entry): IO[Unit]

  def loadItems(
      endpoint: Endpoint,
      secret: String,
      before: Option[String]
  ): IO[Either[Error, List[Entry]]] = {
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
        response <- request.send(backend)
        body <- IO(response.body.getOrElse(""))
        _ <-
          if (!response.code.isSuccess)
            logger.error(s"Received ${response.code} from $url")
          else
            IO.unit
        result <- IO(decode[RedditListing](body).map(Entry.fromListing(_)))
      } yield (result)
    }
  }

  def getBefore(
      items: Either[Error, List[Entry]]
  ): Option[String] =
    items.toOption.map(_.headOption).flatten.map(_.name)

  def handleItems(
      xa: HikariTransactor[IO],
      items: Either[Error, List[Entry]]
  ): IO[List[Unit]] = {
    items match {
      case Right(items) =>
        items.traverse(entry => {
          handleEntry(xa, entry)
        })
      case Left(e) =>
        for {
          _ <- logger.error(s"Error parsing data: $e")
        } yield (List())
    }
  }

  def startLoopFor(
      xa: HikariTransactor[IO],
      endpoint: Endpoint,
      secret: String,
      state: Option[String],
      delay: FiniteDuration
  ): IO[Unit] =
    state.iterateForeverM { before =>
      {
        for {
          items <- loadItems(endpoint, secret, before)
          _ <- handleItems(xa, items)
          _ <- IO.sleep(delay)
        } yield (getBefore(items))
      }
    }

  def loop(xa: HikariTransactor[IO], secret: String): IO[Unit] = {
    val commentsIO = startLoopFor(xa, Comments, secret, None, 2.seconds)
    val postsIO = startLoopFor(xa, Posts, secret, None, 30.seconds)

    for {
      fc <- commentsIO.start
      fp <- postsIO.start
      _ <- fc.join
      _ <- fp.join
    } yield ()
  }
}

sealed trait RedditJsonCodec

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
