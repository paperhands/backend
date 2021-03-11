package app.paperhands.concurrent

import cats._
import cats.effect._
import cats.implicits._
import cats.effect.concurrent.Ref

// Unbound channel implementation
final class Chan[A](ref: Ref[IO, Vector[A]]) {
  def take: IO[Option[A]] =
    ref.getAndUpdate(_.drop(1)).map(_.headOption)

  def put(i: A): IO[Unit] =
    ref.update(_ :+ i)

  def append(is: Seq[A]): IO[Unit] =
    ref.update(_ ++ is)

  def length: IO[Int] =
    ref.get.map(_.length)
}

object Chan {
  def apply[A](): IO[Chan[A]] =
    Ref.of[IO, Vector[A]](Vector()).map(ref => new Chan[A](ref))
}
