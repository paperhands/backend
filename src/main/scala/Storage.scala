package app.paperhands.storage

import app.paperhands.config.Cfg
import app.paperhands.model
import doobie._
import doobie.implicits._
import cats._
import cats.effect._
import cats.implicits._
import doobie.util.ExecutionContexts

object Storage extends Cfg with model.DoobieMetas {
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    s"jdbc:postgresql:${cfg.repository.database}",
    cfg.repository.user,
    cfg.repository.password
  )

  def saveSentiments(sents: List[model.Sentiment]) = {
    val sql = """
      INSERT INTO
      sentiments(created_time, symbol, origin_id, score, confidence)
      VALUES(now(), ?, ?, ?, -1)
    """
    val prog = Update[model.Sentiment](sql).updateMany(sents)

    val io = prog.transact(xa)
    io.unsafeRunSync
  }

  def saveContent(entry: model.Content) = {
    val sql = """
      INSERT INTO
      content(id, type, origin, parent_id, permalink, author, body, origin_time, parsed, created_time)
      VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, now())
      ON CONFLICT DO NOTHING
    """
    val prog = Update[model.Content](sql).run(entry)

    val io = prog.transact(xa)
    io.unsafeRunSync
  }
}
