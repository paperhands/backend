package app.paperhands.concurrent

import cats._
import cats.effect._
import cats.implicits._
import cats.effect.concurrent._

import scala.concurrent._

// Unbound channel implementation
// Uses MVar for locking if chan is empty
final class Chan[A](ref: Ref[IO, Vector[A]], mvar: MVar2[IO, Unit]) {
  implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.Implicits.global)

  // Take head value from Chan
  // if len == 0 block using MVar
  def take: IO[Option[A]] =
    for {
      _ <- mvar.take
      head <- ref.getAndUpdate(_.drop(1)).map(_.headOption)
    } yield head

  // Put single item onto a chan
  // Always unblocks MVar
  def put(i: A): IO[Unit] =
    for {
      _ <- ref.update(_ :+ i)
      _ <- mvar.put(()).start
    } yield ()

  // Put multiple items onto a chan
  // Always unblocks MVar
  def append(is: Seq[A]): IO[Unit] =
    for {
      _ <- ref.update(_ ++ is)
      _ <- mvar.put(()).replicateA(is.length).start
    } yield ()

  // Lookup length of underlying Ref with Vector
  def length: IO[Int] =
    ref.get.map(_.length)
}

object Chan {
  implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.Implicits.global)

  def apply[A](): IO[Chan[A]] =
    for {
      ref <- Ref.of[IO, Vector[A]](Vector())
      mvar <- MVar.of[IO, Unit](())
      _ <- mvar.take
    } yield new Chan[A](ref, mvar)
}
