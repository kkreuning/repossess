package nl.lunatech.daywalker.sqlite

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import java.time.format.DateTimeFormatter
import nl.lunatech.daywalker.Author
import nl.lunatech.daywalker.Commit
import nl.lunatech.daywalker.Database
import nl.lunatech.daywalker.FileSnapshot
import zio.Task
import zio.interop.catz._

class SqliteDatabase(xa: Transactor[Task]) extends Database[Task] {
  // import SqliteDatabase._

  private type Hash = String

  def reset: Task[Unit] = ???
  // Task.collectAll(SQL.destroy.map(_.run.transact(xa))) *>
  //   Task.collectAll(SQL.create.map(_.run.transact(xa))) *>
  //   Task.unit

  def listHashes: Task[Vector[Hash]] =
    sql"SELECT hash FROM commits".query[String].to[Vector].transact(xa)

  def persistCommit(commit: Commit): Task[Unit] = {
    def insertAuthor(author: Author) =
      sql"""
        INSERT OR IGNORE INTO authors(name, email_address)
        VALUES (
          ${author.name},
          ${author.emailAddress}
        );
      """.update

    // FIXME: Add repo
    def insertCommit(commit: Commit) =
      sql"""
        INSERT INTO commits(hash, parent, repo, date, tag, message, author_name)
        VALUES (
          ${commit.hash},
          ${commit.parent.getOrElse("NULL")},
          "primary",
          ${commit.date.format(DateTimeFormatter.ISO_DATE_TIME)},
          ${commit.tag.getOrElse("NULL")},
          ${commit.message},
          ${commit.author.name}
        );
      """.update

    for {
      _ <- insertAuthor(commit.author).run.transact(xa)
      _ <- insertCommit(commit).run.transact(xa)
    } yield ()
  }

  def persistFileSnapshots(hash: Hash, snapshots: Set[FileSnapshot]): Task[Unit] = {
    def insertFiles(snapshots: Set[FileSnapshot]) =
      Update[(String, String, String, String, String)](
        """
        INSERT OR IGNORE INTO files(path, language, file_name, namespace, scope)
        VALUES (?, ?, ?, ?, ?);
      """
      ).updateMany(
          snapshots.map { s =>
            (s.path.toString, s.language, s.fileName, s.namespace.getOrElse("NULL"), s.scope.toString)
          }.toList
        )

    def insertSnapshots(hash: String, snapshots: Set[FileSnapshot]) =
      Update[(String, String, Int, Int, Int, Int, Int, Int, Int, Boolean)](
        """
          INSERT OR IGNORE INTO snapshots(commit_hash, file_path, lines_added, lines_deleted, code_lines, comment_lines, blank_lines, total_lines, complexity, changed)
          VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """
      ).updateMany(
          snapshots.map { s =>
            (
              hash,
              s.path.toString,
              s.linesAdded,
              s.linesDeleted,
              s.codeLines,
              s.commentLines,
              s.blankLines,
              s.allLines,
              s.complexity,
              s.changed
            )
          }.toList
        )

    for {
      _ <- insertFiles(snapshots).transact(xa)
      _ <- insertSnapshots(hash, snapshots).transact(xa)
    } yield ()
  }
}
