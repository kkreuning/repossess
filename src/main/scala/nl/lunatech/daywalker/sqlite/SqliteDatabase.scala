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
  import SqliteDatabase._

  private type Hash = String

  def reset: Task[Unit] =
    Task.collectAll(SQL.destroy.map(_.run.transact(xa))) *>
      Task.collectAll(SQL.create.map(_.run.transact(xa))) *>
      Task.unit

  def listHashes: Task[Vector[Hash]] =
    SQL.selectCommitHashes.to[List].transact(xa).map(_.toVector)

  def persistCommit(commit: Commit): Task[Unit] =
    SQL.insertAuthor(commit.author).run.transact(xa) *>
      SQL.insertCommit(commit).run.transact(xa) *>
      Task.unit

  def persistFileSnapshots(hash: Hash, fileSnapshots: Set[FileSnapshot]): Task[Unit] =
    SQL.insertFiles(fileSnapshots).transact(xa) *>
      SQL.insertSnapshots(hash, fileSnapshots).transact(xa) *>
      Task.unit
}

object SqliteDatabase {
  object SQL {
    val destroy: Seq[Update0] = Seq(
      sql"DROP TABLE IF EXISTS authors;".update,
      sql"DROP TABLE IF EXISTS commits;".update,
      sql"DROP TABLE IF EXISTS files;".update,
      sql"DROP TABLE IF EXISTS snapshots;".update,
      sql"DROP INDEX IF EXISTS IX_snapshots_commit_hash;".update,
      sql"DROP INDEX IF EXISTS IX_snapshots_file_path;".update,
      sql"DROP TABLE IF EXISTS latest_commit;".update,
      sql"DROP INDEX IF EXISTS UQ_latest_commit_commit_hash;".update,
    )

    def create: Seq[Update0] = Seq(
      sql"""
      CREATE TABLE IF NOT EXISTS authors(
        name TEXT PRIMARY KEY,
        email_address TEXT NOT NULL
      );""".update,
      sql"""CREATE TABLE IF NOT EXISTS commits(
        hash TEXT PRIMARY KEY,
        parent TEXT NULL,
        tag TEXT NULL,
        date TEXT NOT NULL,
        author_name TEXT NOT NULL,
        message TEXT NOT NULL,
        FOREIGN KEY(author_name) REFERENCES authors(name)
      );""".update,
      sql"""CREATE TABLE IF NOT EXISTS files(
        path TEXT PRIMARY KEY,
        language TEXT NOT NULL,
        file_name TEXT NOT NULL,
        namespace TEXT NOT NULL,
        scope TEXT NOT NULL
      );""".update,
      sql"""CREATE TABLE IF NOT EXISTS snapshots(
        commit_hash TEXT NOT NULL,
        file_path TEXT NOT NULL,
        lines_added INTEGER NOT NULL,
        lines_removed INTEGER NOT NULL,
        all_lines INTEGER NOT NULL,
        code_lines INTEGER NOT NULL,
        comment_lines INTEGER NOT NULL,
        blank_lines INTEGER NOT NULL,
        complexity INTEGER NOT NULL,
        changed BOOLEAN NOT NULL,
        FOREIGN KEY(commit_hash) REFERENCES commits(hash),
        FOREIGN KEY(file_path) REFERENCES files(path)
      );""".update,
      sql"CREATE INDEX IF NOT EXISTS IX_snapshots_commit_hash ON snapshots(commit_hash)".update,
      sql"CREATE INDEX IF NOT EXISTS IX_snapshots_file_path ON snapshots(file_path)".update,
      sql"""
        CREATE TABLE IF NOT EXISTS latest_commit(
          idx INTEGER INTEGER NOT NULL,
          commit_hash INTEGER NOT NULL,
          date TEXT NOT NULL,
          FOREIGN KEY(commit_hash) REFERENCES commits(hash)
        )
        """.update,
      sql"CREATE UNIQUE INDEX IF NOT EXISTS UQ_latest_commit_commit_hash ON latest_commit(idx)".update
    )

    def selectCommitHashes: Query0[String] =
      sql"SELECT hash FROM commits".query[String]

    def insertAuthor(author: Author): Update0 =
      sql"""
        INSERT OR IGNORE INTO authors(name, email_address)
        VALUES (${author.name}, ${author.emailAddress});
      """.update

    def insertCommit(commit: Commit): Update0 =
      sql"""
        INSERT INTO commits(hash, parent, tag, date, author_name, message)
        VALUES (
          ${commit.hash},
          ${commit.parent.getOrElse("NULL")},
          ${commit.tag.getOrElse("NULL")},
          ${commit.date.format(DateTimeFormatter.ISO_DATE_TIME)},
          ${commit.author.name},
          ${commit.message}
        );
      """.update

    def insertFiles(snapshots: Set[FileSnapshot]) = {
      Update[(String, String, String, String, String)](
        """
          INSERT OR IGNORE INTO files(path, language, file_name, namespace, scope)
          VALUES (?, ?, ?, ?, ?);
        """
      ).updateMany(
        snapshots
          .map(fs => (fs.path.toString, fs.language, fs.fileName, fs.namespace.getOrElse("NULL"), fs.scope.toString))
          .toList
      )
    }

    def insertSnapshots(hash: String, snapshots: Set[FileSnapshot]) = {
      Update[(String, String, Int, Int, Int, Int, Int, Int, Int, Boolean)](
        """
          INSERT INTO snapshots(commit_hash, file_path, lines_added, lines_removed, all_lines, code_lines, comment_lines, blank_lines, complexity, changed)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """
      ).updateMany(
        snapshots
          .map(
            fs =>
              (
                hash,
                fs.path.toString,
                fs.linesAdded,
                fs.linesRemoved,
                fs.allLines,
                fs.codeLines,
                fs.commentLines,
                fs.blankLines,
                fs.complexity,
                fs.changed
              )
          )
          .toList
      )
    }
  }
}
