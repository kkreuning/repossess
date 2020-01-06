package nl.lunatech.daywalker

import java.nio.file.Paths
import nl.lunatech.daywalker.jgit.JGitRepositoryService
import nl.lunatech.daywalker.scc.SccDirectoryAnalyzer
import scala.concurrent.ExecutionContext
import zio.Cause
import zio.Exit
import zio.FiberFailure
import zio.IO
import zio.Runtime
import zio.Task
import zio.internal.PlatformLive

object Main {
  def main(args: Array[String]): Unit = {
    val dir = Paths.get("/Users/kay/Development/ING/profilenotificationsyncsv")
    val branch = "refs/heads/development"

    val repositoryService = new JGitRepositoryService(dir, branch)

    val commits = unsafeRunSync(
      for {
        hashes <- repositoryService.listCommitHashes
        commits <- Task.collectAll(hashes.map(repositoryService.readCommit(_)))
      } yield commits.flatten
    )

    println(commits)

    val lastCommitHash = commits.last.hash

    println(s"Last known commit hash: ${lastCommitHash.toString}")

    val analyzer = new SccDirectoryAnalyzer()
    val analysises = unsafeRunSync(analyzer.analyzeDirectory(dir, _ == "Scala"))

    println(analysises.mkString("\n"))

    // for each commit:
    //   - checkout commit
    //   - run analysis
    //   - store analysis in db
    // cleanup: checkout last commit
  }

  private val runtime = Runtime((), PlatformLive.fromExecutionContext(ExecutionContext.global))

  private def unsafeRunSync[E, A](io: IO[E, A]): A = {
    runtime.unsafeRunSync(io) match {
      case Exit.Success(v)                    => v
      case Exit.Failure(Cause.Die(exception)) => throw exception
      case Exit.Failure(Cause.Interrupt(_))   => throw new InterruptedException
      case Exit.Failure(failure)              => throw FiberFailure(failure)
    }
  }
}
