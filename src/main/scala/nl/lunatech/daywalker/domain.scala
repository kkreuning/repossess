package nl.lunatech.daywalker

import java.time.LocalDateTime
import java.nio.file.Path

final class Hash(val value: String) extends AnyVal {
  override def toString: String = value
}
object Hash {
  def apply(value: String): Hash = new Hash(value);
}
final case class Author(name: String, emailAddress: String)
sealed trait Change { val path: Path }
object Change {
  final case class Add(path: Path) extends Change
  final case class Copy(path: Path) extends Change
  final case class Delete(path: Path) extends Change
  final case class Modify(path: Path) extends Change
  final case class Rename(path: Path) extends Change
}
final case class Commit(
    hash: Hash,
    parentHash: Option[Hash],
    date: LocalDateTime,
    author: Author,
    message: String,
    changes: Seq[Change]
  )

final case class SourceFileAnalysis(
    language: String,
    path: Path,
    filename: String,
    lines: Int,
    blanks: Int,
    comments: Int,
    code: Int
  )
