package app.paperhands.storage

import java.time.Instant

import app.paperhands.io.AddContextShift
import app.paperhands.config.Cfg
import app.paperhands.model

import doobie._
import doobie.implicits._
import doobie.hikari._

import cats._
import cats.effect._
import cats.implicits._

import fs2._

import scala.concurrent._

trait ConnectionPool {
  val transactor = ConnectionPool.transactor
}

object ConnectionPool extends Cfg with AddContextShift {
  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](
        cfg.repository.max_conns
      ) // our connect EC
      be <- Blocker[IO] // our blocking EC
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        s"jdbc:postgresql://${cfg.repository.host}:${cfg.repository.port}/${cfg.repository.database}",
        cfg.repository.user,
        cfg.repository.password,
        ce, // await connection here
        be // execute JDBC operations here
      )
    } yield xa
}

object Storage extends model.DoobieMetas {
  def saveSentiments(sents: List[model.Sentiment]): ConnectionIO[Int] = {
    val sql = """
      INSERT INTO
      sentiments(symbol, origin_id, score, created_time, confidence)
      VALUES(?, ?, ?, ?, -1)
    """
    Update[model.Sentiment](sql).updateMany(sents)
  }

  def saveEngagements(sents: List[model.Engagement]): ConnectionIO[Int] = {
    val sql = """
    INSERT INTO
    engagements(symbol, origin_id, created_time)
    VALUES(?, ?, ?)
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

  def getPopularityForInterval(
      symbol: String,
      start: Instant,
      end: Instant
  ): ConnectionIO[model.Popularity] =
    sql"""
      SELECT
        $symbol as symbol,
        (
          SELECT
            COUNT(DISTINCT c.author) AS value
          FROM sentiments AS s
          INNER JOIN content AS c ON s.origin_id = c.id
          WHERE
           s.symbol = $symbol
           and s.created_time > $start
           and s.created_time < $end
          ORDER BY
            value DESC
        ) as mentions,
       (
         SELECT
           COUNT(DISTINCT c.author) AS value
         FROM engagements AS e
         INNER JOIN content AS c ON e.origin_id = c.id
         WHERE
           e.symbol = $symbol
           and e.created_time > $start
           and e.created_time < $end
         ORDER BY
           value DESC
       ) as engagements
    """
      .query[model.Popularity]
      .unique

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

  def getContentForExport: Stream[ConnectionIO, model.Content] =
    sql"""
      SELECT
        id, type, origin, parent_id, permalink, author, body, origin_time, parsed
      FROM content
      WHERE body IS NOT NULL
    """
      .query[model.Content]
      .stream
}
