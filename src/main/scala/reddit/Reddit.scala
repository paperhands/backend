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
import cats.effect.concurrent.Ref

import scala.concurrent._
import scala.concurrent.duration._

import app.paperhands.io.{Logger, HttpBackend}
import app.paperhands.concurrent._

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

trait Reddit extends HttpBackend {
  implicit val timer = IO.timer(ExecutionContext.global)

  val logger = Logger("reddit")

  def handleEntry(xa: HikariTransactor[IO], entry: Entry): IO[Unit]

  def loadItems(
      endpoint: Endpoint,
      secret: String,
      username: String,
      before: Option[String]
  ): IO[Either[Throwable, List[Entry]]] = {
    val limit = 100
    val ts = System.currentTimeMillis
    val ua = s"linux:$secret:0.0.1-$ts (by u/$username)"
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

    backend
      .use { implicit backend =>
        for {
          response <- request.send(backend)
          body <- IO(response.body.getOrElse(""))
          result <- IO(decode[RedditListing](body).map(Entry.fromListing(_)))
        } yield result
      }
      .handleErrorWith(e =>
        for {
          _ <- logger.error(
            s"Error querying reddit $url: $e\n${e.getStackTrace.mkString("\n")}"
          )
        } yield Left(e)
      )
  }

  // Update state by prepending first id from a list
  // reddit is like a linked list, and we constantly ask for items before some id
  // problem is: if we have ID that gets deleted we will always get empty response
  // to fix that we store last 20 ids in reverse order
  // if we get response we prepend new id to a state list
  // if we get empty response we drop 1 id (that got us empty response back)
  // and reuse previous one
  // that causes some duplication (since we need to process same id twice)
  // but pretty minimal one
  def updateState(
      items: Either[Throwable, List[Entry]],
      state: List[String]
  ): List[String] =
    items match {
      case Right(items) if items.length == 0 => state.drop(1)
      case Right(items)                      => items.map(_.name) ++ state
      case Left(_)                           => state.drop(1)
    }

  def addItemsToQueue(items: Vector[Entry], chan: Chan[Entry]) =
    chan.append(items)

  def handleItems(
      xa: HikariTransactor[IO],
      endpoint: Endpoint,
      items: Either[Throwable, List[Entry]],
      chan: Chan[Entry]
  ): IO[Unit] = {
    items match {
      case Right(items) =>
        logger.info(s"Adding ${items.length} entries to the $endpoint chan") *>
          addItemsToQueue(items.toVector, chan)
      case Left(_) => IO()
    }
  }

  def calculateSleep(endpoint: Endpoint, length: Int): IO[Unit] = {
    val duration = endpoint match {
      // length 0 for comments probably indicates that latest comment was deleted
      // so solution would be to start from scratch as quickly as possible
      case Comments if length > 99 || length == 0 => 2.seconds
      case Posts if length > 99                   => 10.seconds
      case _ if length > 70                       => 3.seconds
      case Comments                               => 4.seconds
      case Posts                                  => 120.seconds
    }

    logger.info(s"Sleeping for $duration for $endpoint") *>
      IO.sleep(duration)
  }

  def producerFor(
      xa: HikariTransactor[IO],
      endpoint: Endpoint,
      secret: String,
      username: String,
      initialState: List[String],
      chan: Chan[Entry]
  ): IO[Unit] =
    initialState.iterateForeverM { state =>
      val before = state.headOption

      for {
        _ <- logger.info(s"querying $endpoint for new items before $before")
        items <- loadItems(endpoint, secret, username, before)
        _ <- handleItems(xa, endpoint, items, chan)
        _ <- calculateSleep(endpoint, items.toList.flatten.length)
      } yield updateState(items, state).take(10)
    }

  def handle(xa: HikariTransactor[IO], e: Option[Entry]): IO[Unit] =
    e match {
      case Some(entry) => handleEntry(xa, entry)
      case None        => IO.unit
    }

  def consumerFor(
      xa: HikariTransactor[IO],
      endpoint: Endpoint,
      chan: Chan[Entry]
  ): IO[Unit] =
    for {
      head <- chan.take
      len <- chan.length
      _ <- IO
        .pure(len > 1000)
        .ifM(
          logger.warn(
            s"We have $len entries left to be processed in the $endpoint chan"
          ),
          IO.unit
        )
    } yield handle(xa, head)

  def produceAndConsume(
      xa: HikariTransactor[IO],
      endpoint: Endpoint,
      secret: String,
      username: String
  ): IO[Unit] =
    for {
      state <- Chan[Entry]()
      f <- producerFor(xa, endpoint, secret, username, List(), state).start
      fh <- consumerFor(xa, endpoint, state).foreverM.start
      _ <- f.join
      _ <- fh.join
    } yield ()

  def loop(
      xa: HikariTransactor[IO],
      secret: String,
      username: String
  ): IO[Unit] =
    for {
      fp <- produceAndConsume(xa, Posts, secret, username).start
      fc <- produceAndConsume(xa, Comments, secret, username).start
      _ <- fc.join
      _ <- fp.join
    } yield ()
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
