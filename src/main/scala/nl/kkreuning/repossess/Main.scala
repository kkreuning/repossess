package nl.kkreuning.repossess

import java.nio.file.Files
import java.nio.file.Paths
import nl.kkreuning.repossess.jgit.JGitRepository
import nl.kkreuning.repossess.scc.SccSnapshotter
import nl.kkreuning.repossess.sqlite.SqliteDatabase
import nl.kkreuning.repossess.sqlite.mkTransactor
import nl.kkreuning.repossess.sqlite.{initialize => initializeDatabase}
import scala.concurrent.ExecutionContext
import scala.util.Try
import zio.Cause
import zio.Task

object Main {
  def main(args: Array[String]): Unit = {
    val program =
      for {
        cwd <- Task(Paths.get("").toAbsolutePath)
        cliArgsParser = new CliArgsParser {}
        cliArgs <- Task.fromEither(cliArgsParser.parse(args.toList, cwd))

        // TODO: Logger based on cliArgs.verbose

        // Show help and exit?
        _ <- if (cliArgs.showHelp) Task(println(cliArgsParser.helpMessage)) *> Task.halt(Cause.empty) else Task.unit

        // Show version and exit?
        _ <- if (cliArgs.showVersion) Task(println("0.0.1")) *> Task.halt(Cause.empty) else Task.unit

        // Reset database by deleting the file
        // TODO: Truncate database using db.reset instead of deleting db file
        _ <- Task.when(cliArgs.resetDatabase)(
          Task(println(s"Deleting database file ${cliArgs.database}")) *>
            Task(Try(Files.delete(Paths.get(cliArgs.database))))
        )

        repo <- Task(new JGitRepository(cliArgs.repository))
        snapper <- Task(new SccSnapshotter(_ => true))

        currentHash <- repo.getCurrentHash

        // determine target branch, either cli provided or current hash
        branch = cliArgs.branch.getOrElse(currentHash)

        allHashes <- repo.listHashes(branch)

        databaseConfig = DatabaseConfig(s"jdbc:sqlite:${cliArgs.database}", "", "")
        ec = ExecutionContext.global
        transactor = mkTransactor(databaseConfig, ec, ec) // TODO: Use ZIO provided execution contexts instead of global

        _ <- transactor.use { xa =>
          for {
            // apply migrations
            _ <- initializeDatabase(xa)

            db <- Task(new SqliteDatabase(xa))

            knownHashes <- db.listHashes

            // only commits that we didn't process already
            newHashes = allHashes diff knownHashes
            commits <- repo.getCommits(newHashes)

            // TODO: Stash any changes

            // persist commits
            _ <- Task.foreach(commits)(commit => db.persistCommit(commit, cliArgs.repoName))

            // file changes per commit
            changedFilesPerCommit <- Task
              .foreach(commits) { commit =>
                repo.listChangedFiles(commit.hash, commit.parent).map(fcs => (commit.hash -> fcs))
              }
              .map(_.toMap)

            // snapshots per commit
            _ <- Task.foreach(commits.zipWithIndex) {
              case (commit, idx) =>
                for {
                  _ <- Task(println(s"$idx / ${commits.size} :: ${commit.hash} - ${commit.message}"))

                  // checkout repo at specific commit
                  _ <- repo.checkout(commit.hash)
                  snapshots0 <- snapper.takeFileSnapshots(cliArgs.repository)
                  maybeChangedFiles = changedFilesPerCommit.get(commit.hash)
                  // FIXME: Snapshot augmentation should happen in the snapper instead of here
                  snapshots = snapshots0.map { snapshot =>
                    snapshot.copy(
                      linesAdded =
                        maybeChangedFiles.fold(0)(_.find(_.path == snapshot.path).map(_.linesAdded).getOrElse(0)),
                      linesDeleted =
                        maybeChangedFiles.fold(0)(_.find(_.path == snapshot.path).map(_.linesDeleted).getOrElse(0)),
                      changed = maybeChangedFiles.fold(false)(_.exists(_.path == snapshot.path))
                    )
                  }

                  _ <- db.persistFileSnapshots(commit.hash, snapshots)
                } yield ()
            }
            // _ = println(branch)
            // _ = print(newHashes)
          } yield ()
        }

        // be kind, rewind
        _ <- repo.checkout(currentHash)

        // TODO: Pop stashed changes
      } yield ()

    unsafeRun(program)
  }

  private val rt =
    zio.Runtime((), zio.internal.PlatformLive.fromExecutionContext(scala.concurrent.ExecutionContext.global))

  private def unsafeRun[E, A](io: zio.IO[E, A]): A = {
    rt.unsafeRunSync(io) match {
      case zio.Exit.Success(v)                      => v
      case zio.Exit.Failure(zio.Cause.Die(ex))      => throw ex
      case zio.Exit.Failure(zio.Cause.Interrupt(_)) => throw new InterruptedException
      case zio.Exit.Failure(cause)                  => throw zio.FiberFailure(cause)
    }
  }
}
