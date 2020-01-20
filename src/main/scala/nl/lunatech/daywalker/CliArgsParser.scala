package nl.lunatech.daywalker

import scala.annotation.tailrec
import java.nio.file.Path
import java.nio.file.Paths

trait CliArgsParser {
  def parse(args: List[String], cwd: Path): Either[Throwable, CliArgs] = {
    val defaultCliArgs = CliArgs("database.db", cwd, None, reset = false, verbose = false, help = false)

    if (args.isEmpty)
      Right(defaultCliArgs.copy(help = true))
    else
      parseNext(args, defaultCliArgs)
  }

  val helpMessage: String = """
                              |Usage: daywalker [options]
                              |
                              |  -db | --database <name>     database to store results in (default to database.db)
                              |  -repo | --repository <path> repository location (defauls to current directory)
                              |  -br | --branch <name>       branch to analyse (defaults to HEAD)
                              |  --reset                     clean the database first
                              |  --verbose                   print more messages
                              |  --help                      show this help message
  """.stripMargin

  @tailrec
  private def parseNext(args: List[String], cliArgs: CliArgs): Either[Throwable, CliArgs] =
    args match {
      // params
      case ("-db" | "--database") :: db :: tail =>
        parseNext(tail, cliArgs.copy(database = db))
      case ("-repo" | "--repository") :: repo :: tail =>
        parseNext(tail, cliArgs.copy(repository = Paths.get(repo)))
      case ("-br" | "--branch") :: br :: tail =>
        parseNext(tail, cliArgs.copy(branch = Some(br)))

      // flags
      case "--reset" :: tail =>
        parseNext(tail, cliArgs.copy(reset = true))
      case "--verbose" :: tail =>
        parseNext(tail, cliArgs.copy(verbose = true))
      case "--help" :: tail =>
        parseNext(tail, cliArgs.copy(help = true))

      case other :: _ =>
        Left(new IllegalArgumentException(s"Unknown argument $other"))

      case Nil =>
        Right(cliArgs)
    }
}
