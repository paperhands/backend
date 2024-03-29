package app.paperhands.flyway

import app.paperhands.io.Logger
import cats.effect._
import com.zaxxer.hikari.HikariDataSource
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.info.MigrationInfoDumper

object MyFlyway {
  val logger = Logger("flyway")
  val location = "classpath:migrations"

  def flyway(ds: HikariDataSource): Flyway =
    Flyway.configure
      .dataSource(ds)
      .locations(location)
      .load()

  def migrate(ds: HikariDataSource): IO[ExitCode] =
    IO(flyway(ds).migrate).as(ExitCode.Success)
  def clean(ds: HikariDataSource): IO[ExitCode] =
    IO(flyway(ds).clean).as(ExitCode.Success)

  def info(ds: HikariDataSource): IO[ExitCode] =
    logger
      .info(
        s"flyway info:\n${MigrationInfoDumper.dumpToAsciiTable(flyway(ds).info.all)}"
      )
      .as(ExitCode.Success)

  def run(xa: HikariTransactor[IO], command: Option[String]): IO[ExitCode] =
    xa.configure { ds =>
      command match {
        case Some("migrate") => migrate(ds)
        case Some("clean")   => clean(ds)
        case Some("info")    => info(ds)
        case _ =>
          logger
            .error(s"Unknown flyway command '$command'")
            .as(ExitCode.Error)
      }
    }
}
