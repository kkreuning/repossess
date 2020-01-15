package nl.lunatech.daywalker

import java.nio.file.Path
import java.time.LocalDateTime

final case class CliArgs(database: String, repository: Path, branch: String, reset: Boolean, verbose: Boolean)

final case class DatabaseConfig(url: String, user: String, pass: String)

sealed trait Scope
object Scope {
  case object Main extends Scope
  case object Test extends Scope
}

final case class FileSnapshot(
    language: String,
    path: Path,
    fileName: String,
    namespace: Option[String],
    scope: Scope,
    linesAdded: Int,
    linesRemoved: Int,
    allLines: Int,
    codeLines: Int,
    commentLines: Int,
    blankLines: Int,
    complexity: Int,
    changed: Boolean,
)

final case class DirectorySnapshot(
    totalFiles: Int,
    totalLines: Int,
    totalCodeLines: Int,
    totalCommentLines: Int,
    totalBlankLines: Int
  )

final case class Author(name: String, emailAddress: String)

final case class Commit(
    hash: String,
    parent: Option[String],
    date: LocalDateTime,
    tag: Option[String],
    author: Author,
    message: String,
    averageComplexity: Float = 0f
  )

final case class FileChange(path: Path, linesAdded: Int, linesDeleted: Int)
