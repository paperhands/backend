package app.paperhands.storage

import app.paperhands.config.Config
import app.paperhands.model
import cats.effect._
import cats.implicits._
import doobie._
import doobie.hikari._
import doobie.implicits._
import fs2._

import java.time.Instant

trait ConnectionPool {
  def transactor(cfg: Config): Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](
        cfg.repository.max_conns
      ) // our connect EC
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        s"jdbc:postgresql://${cfg.repository.host}:${cfg.repository.port}/${cfg.repository.database}",
        cfg.repository.user,
        cfg.repository.password,
        ce // await connection here
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
      content(id, type, origin, parent_id, permalink, author, body, origin_time, parsed, subreddit, created_time)
      VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now() at time zone 'utc')
    """
    Update[model.Content](sql).run(entry)
  }

  def createLabel(contentID: String, label: Int): ConnectionIO[Int] = {
    sql"""
      INSERT INTO
      labels(created_time, content_id, label)
      VALUES(now() at time zone 'utc', $contentID, $label)
      ON CONFLICT DO NOTHING
    """.update.run
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

  def getPopularityUniqueUsersForInterval(
      table: String,
      symbol: String,
      start: Instant,
      end: Instant
  ): ConnectionIO[Int] =
    (Fragment.const(s"""
        SELECT
          COUNT(DISTINCT c.author) AS value
        FROM $table AS s
        INNER JOIN content AS c ON s.origin_id = c.id
        WHERE
      """) ++
      fr"""
         s.symbol = $symbol
         and s.created_time > $start
         and s.created_time < $end
      """)
      .query[Int]
      .unique

  def getPopularityCommentsForInterval(
      table: String,
      symbol: String,
      start: Instant,
      end: Instant
  ): ConnectionIO[Int] =
    (Fragment.const(s"""
        SELECT
          COUNT(*) AS value
        FROM $table AS s
        WHERE
      """) ++
      fr"""
         s.symbol = $symbol
         and s.created_time > $start
         and s.created_time < $end
      """)
      .query[Int]
      .unique

  def getPopularityForInterval(
      symbol: String,
      start: Instant,
      end: Instant
  ): ConnectionIO[model.Popularity] =
    for {
      mentions <- getPopularityCommentsForInterval(
        "sentiments",
        symbol,
        start,
        end
      )
      mentionUsers <- getPopularityUniqueUsersForInterval(
        "sentiments",
        symbol,
        start,
        end
      )
      engagements <- getPopularityCommentsForInterval(
        "engagements",
        symbol,
        start,
        end
      )
      engagementUsers <- getPopularityUniqueUsersForInterval(
        "engagements",
        symbol,
        start,
        end
      )
    } yield model.Popularity(
      symbol,
      mentions,
      mentionUsers,
      engagements,
      engagementUsers
    )

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
        id, type, origin, parent_id, permalink, author, body, origin_time, parsed, subreddit
      FROM content
      WHERE body IS NOT NULL
    """
      .query[model.Content]
      .stream

  def getSamples(
      symbol: String,
      limit: Int
  ): ConnectionIO[List[model.Content]] =
    sql"""
      SELECT
        id, type, origin, parent_id, permalink, author, body, origin_time, parsed, subreddit
      FROM content
      WHERE id IN (SELECT origin_id FROM sentiments WHERE symbol = $symbol)
      ORDER BY RANDOM()
      LIMIT $limit
    """
      .query[model.Content]
      .to[List]

  def getUnlabeledContent(
      limit: Int
  ): ConnectionIO[List[model.Content]] =
    sql"""
      SELECT
        id, type, origin, parent_id, permalink, author, body, origin_time, parsed, subreddit
      FROM content
      WHERE id NOT IN (SELECT content_id FROM labels)
      ORDER BY RANDOM()
      LIMIT $limit
    """
      .query[model.Content]
      .to[List]

  def contentExists(
      id: String
  ): ConnectionIO[Boolean] =
    sql"""
      SELECT COUNT(1)
      FROM content
      WHERE id = $id
    """
      .query[Int]
      .unique
      .map(_ > 0)

  def saveOcrCache(
      entry: model.OcrCache
  ): ConnectionIO[Int] = {
    val sql = """
      INSERT INTO
      ocr_cache(url, output, created_time)
      VALUES(?, ?, now() at time zone 'utc')
    """
    Update[model.OcrCache](sql).run(entry)
  }

  def getOcrCache(
      url: String
  ): ConnectionIO[Option[model.OcrCache]] =
    sql"""
      SELECT url, output
      FROM ocr_cache
      WHERE url = $url
    """
      .query[model.OcrCache]
      .option
}
