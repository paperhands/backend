package app.paperhands.model

import app.paperhands.reddit.Entry
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.postgresql.util.PGobject

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

trait DoobieMetas {
  import doobie.util.meta._
  import doobie.implicits.javasql.TimestampMeta

  implicit val JavaTimeInstantMeta: Meta[java.time.Instant] =
    TimestampMeta.imap(t => {
      t.toLocalDateTime().atOffset(ZoneOffset.UTC).toInstant
    })(i => {
      java.sql.Timestamp.valueOf(i.atZone(ZoneId.of("UTC")).toLocalDateTime())
    })

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

object Popularity {}

case class Trending(
    symbol: String,
    popularity: Double
)

case class TimeSeries(
    symbol: String,
    value: Int,
    time: Instant
)

case class Popularity(
    symbol: String,
    mentions: Int,
    mention_users: Int,
    engagements: Int,
    engagement_users: Int
)

case class OcrCache(
    url: String,
    output: String
)
