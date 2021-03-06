package nl.kkreuning.repossess

import java.nio.file.Path
import java.time.LocalDateTime

final case class CliArgs(
    repository: Path,
    repoName: String,
    branch: Option[String],
    database: String,
    resetDatabase: Boolean,
    isVerbose: Boolean,
    showVersion: Boolean,
    showHelp: Boolean
  )

final case class DatabaseConfig(url: String, user: String, pass: String)

final case class FileSnapshot(
    language: String,
    path: Path,
    fileName: String,
    namespace: Option[String],
    scope: String,
    linesAdded: Int,
    linesDeleted: Int,
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

sealed trait FileChangeType
object FileChangeType {
  case object Add extends FileChangeType
  case object Delete extends FileChangeType
  case object Modify extends FileChangeType
  case object Copy extends FileChangeType
  case object Rename extends FileChangeType
}
final case class FileChange(path: Path, `type`: FileChangeType, linesAdded: Int, linesDeleted: Int)
