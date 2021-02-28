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
}
