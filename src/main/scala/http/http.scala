package app.paperhands.http

import cats._
import cats.effect._
import cats.implicits._

import sttp.client3._
import sttp.client3.http4s._

import app.paperhands.io.AddContextShift

trait HttpBackend extends AddContextShift {
  val backend =
    Blocker[IO].flatMap(Http4sBackend.usingDefaultBlazeClientBuilder[IO](_))
}
