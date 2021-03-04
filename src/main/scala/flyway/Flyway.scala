package app.paperhands.flyway

import cats._
import cats.effect._
import cats.implicits._

import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.info.MigrationInfoDumper
import app.paperhands.config.{Config, Cfg}
import app.paperhands.io.Logger

object MyFlyway extends Cfg {
  val logger = Logger("flyway")
  val location = "classpath:migrations"

  def flyway: Flyway =
    Flyway.configure
      .dataSource(
        s"jdbc:postgresql://${cfg.repository.host}:${cfg.repository.port}/${cfg.repository.database}",
        cfg.repository.user,
        cfg.repository.password
      )
      .locations(location)
      .load()

  def migrate: IO[ExitCode] =
    IO(flyway.migrate).as(ExitCode.Success)
  def clean: IO[ExitCode] =
    IO(flyway.clean).as(ExitCode.Success)

  def info: IO[ExitCode] =
    logger
      .info(
        s"flyway info:\n${MigrationInfoDumper.dumpToAsciiTable(flyway.info.all)}"
      )
      .as(ExitCode.Success)

  def run(command: Option[String]): IO[ExitCode] =
    command match {
      case Some("migrate") => migrate
      case Some("clean")   => clean
      case Some("info")    => info
      case _ =>
        logger
          .error(s"Unknown flyway command '$command'")
          .as(ExitCode.Error)
    }
}
