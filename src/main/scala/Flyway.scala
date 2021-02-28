package app.paperhands.flyway

import cats._
import cats.effect._
import cats.implicits._

import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.info.MigrationInfoDumper
import app.paperhands.config.{Config, Cfg}

object MyFlyway extends Cfg {
  val location = "classpath:migrations"

  def flyway: Flyway =
    Flyway.configure
      .dataSource(
        s"jdbc:postgresql:${cfg.repository.database}",
        cfg.repository.user,
        cfg.repository.password
      )
      .locations(location)
      .load()

  def migrate: IO[ExitCode] =
    IO(flyway.migrate).flatMap(_ => IO(ExitCode.Success))
  def clean: IO[ExitCode] =
    IO(flyway.clean).flatMap(_ => IO(ExitCode.Success))
  def info: IO[ExitCode] =
    IO(print(MigrationInfoDumper.dumpToAsciiTable(flyway.info.all))).flatMap(
      _ => IO(ExitCode.Success)
    )

  def run(command: String): IO[ExitCode] =
    command match {
      case "migrate" => migrate
      case "clean"   => clean
      case "info"    => info
      case _         => IO.pure(ExitCode.Error)
    }
}
