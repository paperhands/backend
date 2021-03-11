package app.paperhands.concurrent

import cats._
import cats.effect._
import cats.implicits._
import cats.effect.concurrent._

import scala.concurrent._

// Unbound channel implementation
final class Chan[A](ref: Ref[IO, Int], mvar: MVar2[IO, A]) {
  implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.Implicits.global)

  // Take head value from Chan
  def take: IO[A] =
    mvar.take <*
      ref.update(_ - 1)

  // Put single item onto a chan
  def put(i: A): IO[Unit] =
    ref.update(_ + 1) *>
      mvar.put(i).start.void

  // Put multiple items onto a chan
  def append(is: Seq[A]): IO[Unit] =
    ref.update(_ + is.length) *>
      is.traverse(mvar.put(_)).start.void

  // Lookup length
  def length: IO[Int] =
    ref.get
}

object Chan {
  implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.Implicits.global)

  def apply[A](): IO[Chan[A]] =
    for {
      ref <- Ref.of[IO, Int](0)
      mvar <- MVar.empty[IO, A]
    } yield new Chan[A](ref, mvar)
}
