package nl.lunatech.daywalker

import java.nio.file.Path
import java.nio.file.Paths
import nl.lunatech.daywalker.jgit.JGitRepository
import nl.lunatech.daywalker.scc.SccSnapshotter
import nl.lunatech.daywalker.sqlite.SqliteDatabase
import nl.lunatech.daywalker.sqlite.mkTransactor
import scala.concurrent.ExecutionContext
import zio.Task
import zio.UIO

object Main {
  def main(args: Array[String]): Unit = { // TODO: Turn into ZIO application

    // TODO: Moar logging

    val prgm = for {
      _ <- Task.unit
      args <- Task.fromEither(parseArgs(args.toList))
      // args = CliArgs("pnssv.db", Paths.get("/Users/kay/Development/ING/profilenotificationsyncsv"), "refs/heads/development", reset = false, verbose = false)
      // args = CliArgs("pnssv2.db", Paths.get("/Users/kay/Development/ING/profilenotificationsyncsv"), "refs/heads/development", reset = true, verbose = false)
      // args = CliArgs("onepam.db", Paths.get("/Users/kay/Development/ING/onepam-api-suite"), "refs/heads/develop", true)

      // TODO: Create logger based on args.verbose flag
      dbConfig = DatabaseConfig(s"jdbc:sqlite:${args.database}", "", "")
      transactorR = mkTransactor(dbConfig, ExecutionContext.global, ExecutionContext.global) // TODO: Use better fitted execution contexts, preferable the ones provided by ZIO
      _ <- transactorR.use { xa =>
        for {
          repo <- Task(new JGitRepository(args.repository))
          snapper <- Task(new SccSnapshotter(l => l == "Scala" || l == "Java"))
          db <- UIO(new SqliteDatabase(xa))

          _ <- if (args.reset) db.reset else Task.unit

          commits <- findNewCommits(repo, db)(args.branch)

          _ = println(s"Found ${commits.size} new commits")

          _ <- persistCommits(db)(commits)

          _ <- Task.collectAll(commits.zipWithIndex.map {
            case (commit, idx) =>
              for {
                _ <- Task.unit
                _ = println(s"$idx / ${commits.size} :: ${commit.hash} - ${commit.message}")
                snapshots <- takeSnapshots(repo, snapper)(commit, args.repository)
                _ <- persistFileSnapshots(db)(commit.hash, snapshots)
              } yield ()
          })
        } yield ()
      }
    } yield () // FIXME: Yield proper exit code

    unsafeRun(prgm)
  }

  private def findNewCommits(repo: GitRepository[Task], db: Database[Task])(branch: String) =
    for {
      knownHashes <- db.listHashes // Find commit hashes that are already processed
      allHashes <- repo.listHashes(branch) // Find commit hashes in the given branch
      newHashes = allHashes diff knownHashes // Find unprocessed hashes
      commits <- repo.getCommits(newHashes) // Find commits to process
    } yield commits

  private def persistCommits(db: Database[Task])(commits: Seq[Commit]) =
    Task.collectAll(commits.map(db.persistCommit))

  private def takeSnapshots(repo: GitRepository[Task], snapper: SnapShotter[Task])(commit: Commit, directory: Path) =
    for {
      _ <- repo.checkout(commit.hash)
      changedFiles <- getChangedFiles(repo)(commit)
      fileSnapshots <- snapper.takeFileSnapshots(directory)
    } yield fileSnapshots.map {
      case fs =>
        fs.copy(
          linesAdded = changedFiles.find(_.path == fs.path).map(_.linesAdded).getOrElse(0),
          linesRemoved = changedFiles.find(_.path == fs.path).map(_.linesDeleted).getOrElse(0),
          changed = changedFiles.exists(_.path == fs.path)
        )
    }

  private def getChangedFiles(repo: GitRepository[Task])(commit: Commit) =
    commit
      .parent
      .fold[Task[Set[FileChange]]] {
        Task.succeed(Set.empty)
      } { parent =>
        repo.listChangedFiles(commit.hash, parent)
      }

  private def persistFileSnapshots(db: Database[Task])(hash: String, fileSnapshots: Set[FileSnapshot]) =
    db.persistFileSnapshots(hash, fileSnapshots)

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

  def parseArgs(args: List[String]): Either[Throwable, CliArgs] = {
    @scala.annotation.tailrec
    def recur(args: List[String], cliArgs: CliArgs): Either[Throwable, CliArgs] = {
      args match {
        case ("--database" | "-db") :: database :: tail => recur(tail, cliArgs.copy(database = database))
        case ("--repository" | "-repo") :: repository :: tail =>
          recur(tail, cliArgs.copy(repository = Paths.get(repository)))
        case ("--branch" | "-br") :: branch :: tail => recur(tail, cliArgs.copy(branch = branch))
        case ("--reset") :: tail                    => recur(tail, cliArgs.copy(reset = true))
        case ("--verbose") :: tail                  => recur(tail, cliArgs.copy(verbose = true))
        case other :: _                             => Left(new IllegalArgumentException(s"Unknown argument $other"))
        case Nil =>
          if (cliArgs.repository == null) Left(new IllegalArgumentException("Missing --repository argument"))
          else if (cliArgs.branch == null) Left(new IllegalArgumentException("Missing --branch argument"))
          else Right(cliArgs)
      }
    }

    recur(args, CliArgs("database.db", null, null, false, false))
  }
}
