import minitest._
import scala.concurrent.duration._
import cats.implicits._
import cats.effect._
import cats.effect.testing.minitest.{IOTestSuite, DeterministicIOTestSuite}

import app.paperhands.concurrent._
import java.util.concurrent.TimeoutException

object ConcurrentTestSuite extends IOTestSuite {
  override val timeout = 10.seconds
  test("len works connectly") {
    for {
      chan <- Chan[Int]()
      _ <- chan.put(1).replicateA(100)
      len <- chan.length
    } yield assertEquals(len, 100)
  }

  test("len works connectly with append") {
    for {
      chan <- Chan[Int]()
      _ <- chan.append(List.range(0, 100))
      len <- chan.length
    } yield assertEquals(len, 100)
  }

  test("chan single consumer") {
    for {
      chan <- Chan[Int]()
      _ <- chan.put(1).replicateA(100)
      _ <- chan.take.replicateA(100)
      len <- chan.length
    } yield assertEquals(len, 0)
  }

  test("chan single consumer with append") {
    for {
      chan <- Chan[Int]()
      _ <- chan.append(List.range(0, 10)).replicateA(10)
      _ <- chan.take.replicateA(100)
      len <- chan.length
    } yield assertEquals(len, 0)
  }

  test("chan mulitple consumer") {
    for {
      chan <- Chan[Int]()
      _ <- chan.put(1).replicateA(100)
      _ <- chan.take.replicateA(10).replicateA(10)
      len <- chan.length
    } yield assertEquals(len, 0)
  }

  test("chan not fully consumed") {
    for {
      chan <- Chan[Int]()
      _ <- chan.put(1).replicateA(100)
      _ <- chan.take.replicateA(10)
      len <- chan.length
    } yield assertEquals(len, 90)
  }

  test("chan not fully consumed will block") {
    for {
      chan <- Chan[Int]()
      _ <- chan.put(1).replicateA(9)
      e <- chan.take
        .replicateA(10)
        .timeout(1.second)
        .as(IO.pure(None))
        .handleErrorWith(e => IO.pure(Some(e)))
      len <- chan.length
    } yield e match {
      case Some(e) =>
        assert(e.isInstanceOf[TimeoutException], "Should get TimeoutException")
      case None => assert(false, "Did not get any error")
    }
  }
}
