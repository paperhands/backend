package app.paperhands.concurrent

import cats.effect._
import cats.effect.std.Queue
import cats.implicits._

// Unbound channel implementation
final class Chan[A](ref: Ref[IO, Int], q: Queue[IO, A]) {
  // Take head value from Chan
  def take: IO[A] =
    q.take <*
      ref.update(_ - 1)

  // Put single item onto a chan
  def put(i: A): IO[Unit] =
    ref.update(_ + 1) >>
      q.offer(i)

  // Put multiple items onto a chan
  def append(is: Seq[A]): IO[Unit] =
    ref.update(_ + is.length) >>
      is.traverse(q.offer(_)).void

  // Lookup length
  def length: IO[Int] =
    ref.get
}

object Chan {
  def apply[A](): IO[Chan[A]] =
    for {
      ref <- IO.ref[Int](0)
      q <- Queue.unbounded[IO, A]
    } yield new Chan[A](ref, q)
}
