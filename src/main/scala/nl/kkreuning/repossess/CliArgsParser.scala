package nl.kkreuning.repossess

import scala.annotation.tailrec
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

trait CliArgsParser {
  val helpMessage: String = """
                              |Usage: repossess [options] [flags] <repository>
                              |
                              |Options:
                              |  -n,  --name <name>      name of the repository (default: repository directory name)
                              |  -br, --branch <branch>  branch to analyse (default: HEAD)
                              |  -db, --database <path>  database to store results (default: <name>.db)
                              |
                              |Flags:
                              |      --reset     reset the database first
                              |      --verbose   print more messages
                              |  -v, --version   show version and quit
                              |  -h, --help      show this help message and quit""".stripMargin

  def parse(args: List[String], cwd: Path): Either[Throwable, CliArgs] = {
    val default = PartialCliArgs(cwd, None, None, None, false, false, false, false)
    parseNextArg(args, default)
      .map { partial =>
        val repository = partial.repository
        val repoName = partial.maybeRepoName.getOrElse(partial.repository.getFileName.toString)
        val branch = partial.maybeBranch
        val database = partial.maybeDatabase.map(n => if (n.endsWith(".db")) n else s"$n.db").getOrElse(s"$repoName.db")

        CliArgs(
          repository,
          repoName,
          branch,
          database,
          resetDatabase = partial.resetDatabase,
          isVerbose = partial.isVerbose,
          showVersion = partial.showVersion,
          showHelp = partial.showVersion
        )
      }
  }
  @tailrec
  private def parseNextArg(args: List[String], partial: PartialCliArgs): Either[Throwable, PartialCliArgs] =
    args match {
      case Nil =>
        Right(partial)

      // options
      case ("-n" | "--name") :: name :: rest =>
        parseNextArg(rest, partial.copy(maybeRepoName = Some(name)))
      case ("-br" | "--branch") :: branch :: rest =>
        parseNextArg(rest, partial.copy(maybeBranch = Some(branch)))
      case ("-db" | "--database") :: database :: rest =>
        parseNextArg(rest, partial.copy(maybeDatabase = Some(database)))

      // flags
      case "--reset" :: rest =>
        parseNextArg(rest, partial.copy(resetDatabase = true))
      case "--verbose" :: rest =>
        parseNextArg(rest, partial.copy(isVerbose = true))
      case ("-v" | "--version") :: rest =>
        parseNextArg(rest, partial.copy(showVersion = true))
      case ("-h" | "--help") :: rest =>
        parseNextArg(rest, partial.copy(showHelp = true))

      // unknown
      case unknown :: _ if unknown.startsWith("-") =>
        Left(new IllegalArgumentException(s"Unknown option $unknown"))

      // default
      case repository :: rest => {
        import Files.{exists, isDirectory}

        val dir = Paths.get(repository)
        val git = dir.resolve(".git")

        if (exists(dir) && isDirectory(dir) && exists(git) && isDirectory(git)) {
          parseNextArg(rest, partial.copy(dir))
        } else {
          Left(new IllegalArgumentException(s"Repository $repository is not a valid Git repository"))
        }
      }
    }

  final private case class PartialCliArgs(
      repository: Path,
      maybeRepoName: Option[String],
      maybeBranch: Option[String],
      maybeDatabase: Option[String],
      resetDatabase: Boolean,
      isVerbose: Boolean,
      showVersion: Boolean,
      showHelp: Boolean,
  )
}
