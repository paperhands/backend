package app.paperhands.model

import doobie._
import doobie.implicits._
import org.postgresql.util.PGobject
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import io.circe.generic.JsonCodec
import java.time.Instant
import app.paperhands.reddit.Entry

trait DoobieMetas {
  import doobie.util.meta._
  import doobie.implicits.javasql.TimestampMeta

  implicit val JavaTimeInstantMeta: Meta[java.time.Instant] =
    TimestampMeta.imap(_.toInstant)(java.sql.Timestamp.from)

  implicit val contentMetaMeta: Meta[ContentMeta] =
    Meta.Advanced
      .other[PGobject]("jsonb")
      .imap[ContentMeta](js =>
        decode[ContentMeta](js.getValue).left.map(e => throw e).merge
      )(cm => {
        val o = new PGobject
        o.setType("jsonb")
        o.setValue(cm.asJson.noSpaces)
        o
      })
}

case class Content(
    id: String,
    kind: String,
    source: String,
    parent_id: Option[String],
    permalink: String,
    author: String,
    body: String,
    created_time: Instant,
    parsed: ContentMeta
)

@JsonCodec case class ContentMeta(symbols: List[String], sentiment: Int)

object Content {
  def fromRedditEntry(
      entry: Entry,
      symbols: List[String],
      sentiment: SentimentValue
  ): Content = {
    Content(
      entry.name,
      entry.kind,
      "reddit",
      entry.parent_id,
      entry.permalink,
      entry.author,
      entry.body,
      entry.created_time,
      ContentMeta(symbols, sentiment.getSentiment)
    )
  }
}

case class SentimentData(
    sentiment: SentimentValue,
    symbols: List[String]
)

trait SentimentValue {
  def getSentiment: Int
}

case class Unknown() extends SentimentValue {
  def getSentiment: Int = 0
}
case class Bull() extends SentimentValue {
  def getSentiment: Int = 1
}
case class Bear() extends SentimentValue {
  def getSentiment: Int = 2
}

case class Sentiment(
    symbol: String,
    origin_id: String,
    sentiment: Int,
    created_time: Instant
)

object Sentiment {
  def fromSymbols(
      symbols: List[String],
      sentiment: SentimentValue,
      origin_id: String,
      created_time: Instant
  ): List[Sentiment] = {
    symbols.map(Sentiment(_, origin_id, sentiment.getSentiment, created_time))
  }
}

case class Engagement(symbol: String, origin_id: String, created_time: Instant)

object Engagement {
  def fromSymbols(
      symbols: List[String],
      origin_id: String,
      created_time: Instant
  ): List[Engagement] = {
    symbols.map(Engagement(_, origin_id, created_time))
  }
}

case class Trending(
    symbol: String,
    popularity: Float
)

case class TimeSeries(
    symbol: String,
    value: Int,
    time: Instant
)
