package app.paperhands.storage

import java.time.Instant

import app.paperhands.io.AddContextShift
import app.paperhands.config.Cfg
import app.paperhands.model

import doobie._
import doobie.implicits._

import cats._
import cats.effect._
import cats.implicits._

import scala.concurrent._

trait ConnectionPool extends Cfg with AddContextShift {
  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    s"jdbc:postgresql:${cfg.repository.database}",
    cfg.repository.user,
    cfg.repository.password
  )
}

object Storage extends model.DoobieMetas {
  def saveSentiments(sents: List[model.Sentiment]): ConnectionIO[Int] = {
    val sql = """
      INSERT INTO
      sentiments(created_time, symbol, origin_id, score, confidence)
      VALUES(now(), ?, ?, ?, -1)
    """
    Update[model.Sentiment](sql).updateMany(sents)
  }

  def saveEngagements(sents: List[model.Engagement]): ConnectionIO[Int] = {
    val sql = """
    INSERT INTO
    engagements(symbol, origin_id, created_time)
    VALUES(?, ?, now())
    """
    Update[model.Engagement](sql).updateMany(sents)
  }

  def saveContent(entry: model.Content): ConnectionIO[Int] = {
    val sql = """
      INSERT INTO
      content(id, type, origin, parent_id, permalink, author, body, origin_time, parsed, created_time)
      VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, now())
      ON CONFLICT DO NOTHING
    """
    Update[model.Content](sql).run(entry)
  }

  def getParsedTree(id: String): ConnectionIO[List[model.ContentMeta]] =
    sql"""
      WITH RECURSIVE tree AS (
        SELECT id, created_time, origin_time, type, origin, parent_id, permalink, parsed
        FROM content
        WHERE id = $id
        UNION ALL
          SELECT c.id, c.created_time, c.origin_time, c.type, c.origin, c.parent_id, c.permalink, c.parsed
          FROM content c
            JOIN tree
            ON c.id = tree.parent_id
      ) SELECT parsed FROM tree
    """
      .query[model.ContentMeta]
      .to[List]

  def getTrending(
      start: Instant,
      end: Instant,
      limit: Int
  ): ConnectionIO[List[model.Trending]] =
    sql"""
      SELECT
        symbol,COUNT(score) AS popularity
      FROM sentiments
      WHERE created_time > $start
        AND created_time < $end
      GROUP BY symbol
      ORDER BY COUNT(score)
      DESC LIMIT $limit
    """
      .query[model.Trending]
      .to[List]

  def getEngagementTimeseries(
      symbol: String,
      bucket: String,
      start: Instant,
      end: Instant
  ): ConnectionIO[List[model.TimeSeries]] =
    sql"""
      SELECT
        symbol,
        COUNT(*) AS value,
        time_bucket($bucket :: interval, created_time) as time_interval
      FROM engagements
      WHERE created_time > $start
        AND created_time < $end
        AND symbol = $symbol
      GROUP BY symbol, time_interval
      ORDER BY time_interval ASC
    """
      .query[model.TimeSeries]
      .to[List]

  def getMentionTimeseries(
      symbol: String,
      bucket: String,
      start: Instant,
      end: Instant
  ): ConnectionIO[List[model.TimeSeries]] =
    sql"""
      SELECT
        symbol,
        COUNT(*) AS value,
        time_bucket($bucket :: interval, created_time) as time_interval
      FROM sentiments
      WHERE created_time > $start
        AND created_time < $end
        AND symbol = $symbol
      GROUP BY symbol, time_interval
      ORDER BY time_interval ASC
    """
      .query[model.TimeSeries]
      .to[List]

  def getSentimentTimeseries(
      symbol: String,
      bucket: String,
      start: Instant,
      end: Instant
  ): ConnectionIO[List[model.TimeSeries]] =
    sql"""
      SELECT
        symbol,
        SUM(
          CASE
            WHEN score = 2 THEN -1
            WHEN score < 2 THEN score
          END
        ) AS value,
        time_bucket($bucket :: interval, created_time) AS time_interval
      FROM
        sentiments
      WHERE created_time > $start
        AND created_time < $end
        AND symbol = $symbol
      GROUP BY
        symbol,
        time_interval
      ORDER BY
        time_interval ASC
    """
      .query[model.TimeSeries]
      .to[List]
}
