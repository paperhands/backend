package app.paperhands.model

import doobie._
import doobie.implicits._
import org.postgresql.util.PGobject
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import io.circe.generic.JsonCodec
import java.util.Date
import java.sql.Timestamp

trait DoobieMetas {
  import doobie.util.meta._

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

case class RedditEntry(
    kind: String,
    id: String,
    name: String,
    author: String,
    permalink: String,
    body: String,
    parent_id: Option[String],
    created_time: Date,
    url: Option[String],
    imageURLs: List[String]
)

case class Content(
    id: String,
    kind: String,
    source: String,
    parent_id: Option[String],
    permalink: String,
    author: String,
    body: String,
    created_time: Date,
    parsed: ContentMeta
)

@JsonCodec case class ContentMeta(symbols: List[String], sentiment: Int)

object Content {
  def fromRedditEntry(
      entry: RedditEntry,
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

case class Sentiment(symbol: String, origin_id: String, sentiment: Int)

object Sentiment {
  def fromSymbols(
      symbols: List[String],
      sentiment: SentimentValue,
      origin_id: String
  ): List[Sentiment] = {
    symbols.map(Sentiment(_, origin_id, sentiment.getSentiment))
  }
}
