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
final case class Change(mutation: Mutation, path: Path)
sealed trait Mutation
object Mutation {
  case object Add extends Mutation
  case object Copy extends Mutation
  case object Delete extends Mutation
  case object Modify extends Mutation
  case object Rename extends Mutation
}
final case class Commit(
    hash: Hash,
    parentHash: Option[Hash],
    date: LocalDateTime,
    author: Author,
    message: String,
    changes: Seq[Change]
  )

final case class FileAnalysis(
    language: String,
    path: Path,
    filename: String,
    lines: Int,
    blanks: Int,
    comments: Int,
    code: Int
  )
