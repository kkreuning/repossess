package nl.lunatech.daywalker

import cats.effect.Blocker
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import scala.concurrent.ExecutionContext
import zio.Managed
import zio.Reservation
import zio.Task
import zio.interop.catz._

package object sqlite {
  def mkTransactor[A](
      config: DatabaseConfig,
      connectEc: ExecutionContext,
      transactEc: ExecutionContext
    ): Managed[Throwable, Transactor[Task]] = {
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
}
