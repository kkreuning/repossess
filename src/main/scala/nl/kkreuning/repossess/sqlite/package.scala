package nl.kkreuning.repossess

import cats.effect.Blocker
import doobie.hikari.HikariTransactor
import scala.concurrent.ExecutionContext
import zio.Managed
import zio.Reservation
import zio.Task
import zio.interop.catz._

package object sqlite {
  import org.flywaydb.core.Flyway
  def mkTransactor[A](
      config: DatabaseConfig,
      connectEc: ExecutionContext,
      transactEc: ExecutionContext
    ): Managed[Throwable, HikariTransactor[Task]] = {
    val xa = HikariTransactor.newHikariTransactor[Task](
      "org.sqlite.JDBC",
      config.url,
      config.user,
      config.pass,
      connectEc,
      Blocker.liftExecutionContext(transactEc)
    )

    val resource = xa
      .allocated
      .map {
        case (transactor, cleanupM) =>
          Reservation(Task.succeed(transactor), _ => cleanupM.orDie)
      }
      .uninterruptible

    Managed(resource)
  }

  def initialize(xa: HikariTransactor[Task]): Task[Unit] = {
    xa.configure { ds =>
      Task {
        val flyway = Flyway.configure.dataSource(ds).load()
        flyway.migrate()
        ()
      }
    }
  }
}
