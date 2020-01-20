package nl.lunatech.daywalker.jgit

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import nl.lunatech.daywalker.Author
import nl.lunatech.daywalker.Commit
import nl.lunatech.daywalker.FileChange
import nl.lunatech.daywalker.FileChangeType
import nl.lunatech.daywalker.GitRepository
import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.util.io.NullOutputStream
import scala.jdk.CollectionConverters._
import zio.Task
import zio.UIO

class JGitRepository(directory: Path) extends GitRepository[Task] {
  private type Hash = String

  private val jgit = JGit.open(directory.toFile) // TODO: Make managed resource
  private val repo = Task(jgit.getRepository)
  private val mailMap = MailMap(directory)

  val getCurrentHash: Task[Hash] = repo.map(_.getFullBranch)

  def checkout(hash: Hash): Task[Unit] = Task(jgit.checkout.setName(hash).call()).unit

  def listHashes(branch: String): Task[Vector[Hash]] =
    for {
      branch <- repo.map(_.resolve(branch))
      commits0 <- Task(jgit.log.add(branch).call)
      commits = commits0.asScala.toVector.reverse
      hashes = commits.map(_.getId.getName)
    } yield hashes

  def getCommits(hashes: Seq[Hash]): Task[Vector[Commit]] = {
    def acquire = repo.map(new RevWalk(_))
    def release(revWalk: RevWalk) = UIO(revWalk.close())
    def use(revWalk: RevWalk): Task[Vector[Commit]] = {
      val getTags = for {
        op <- UIO(jgit.tagList)
        refs <- Task(op.call().asScala)
        hashesWithNames = refs.map(ref => (ref.getObjectId.getName -> ref.getName.stripPrefix("refs/tags/")))
        map = hashesWithNames.toMap
      } yield map

      def parseCommit(hash: Hash, tag: Option[String]) =
        for {
          resolved <- repo.map(_.resolve(hash))
          revCommit <- Task(revWalk.parseCommit(resolved))
        } yield Commit(
          revCommit.getId.getName,
          Option.when(revCommit.getParentCount > 0)(revCommit.getParent(0).getName()),
          LocalDateTime.ofEpochSecond(revCommit.getCommitTime, 0, java.time.ZoneOffset.UTC),
          tag,
          mailMap.canonicalize(Author(revCommit.getAuthorIdent.getName, revCommit.getAuthorIdent.getEmailAddress)),
          revCommit.getShortMessage
        )

      for {
        tags <- getTags
        commits <- Task.collectAll(hashes.map(hash => parseCommit(hash, tags.get(hash))))
      } yield commits.toVector
    }

    Task.bracket(acquire, release, use)
  }

  def listChangedFiles(newHash: String, maybeOldHash: Option[String]): Task[Set[FileChange]] = {
    def treeIteratorForCommit(maybeHash: Option[String], reader: ObjectReader) =
      maybeHash
        .fold[Task[AbstractTreeIterator]] {
          Task.succeed(new EmptyTreeIterator())
        } { hash =>
          for {
            rev <- repo.map(_.resolve(s"$hash^{tree}"))
            tree <- Task(new CanonicalTreeParser(null, reader, rev))
          } yield tree
        }

    def mkFormatter(repo: Repository): DiffFormatter = {
      val df = new DiffFormatter(NullOutputStream.INSTANCE)
      df.setRepository(repo)
      df
    }

    for {
      reader <- repo.map(_.newObjectReader)
      oldTreeIterator <- treeIteratorForCommit(maybeOldHash, reader)
      newTreeIterator <- treeIteratorForCommit(Some(newHash), reader)
      formatter <- repo.map(mkFormatter)
      // entries <- Task(formatter.scan(oldTreeIterator, newTreeIterator).asScala)
      diff = jgit.diff.setOldTree(oldTreeIterator).setNewTree(newTreeIterator)
      entries <- Task(diff.call.asScala)
      changedFiles = entries.collect {
        case entry =>
          val path = if (entry.getNewPath != "/dev/null") entry.getNewPath else entry.getOldPath
          val changeType = entry.getChangeType match {
            case ChangeType.ADD    => FileChangeType.Add
            case ChangeType.COPY   => FileChangeType.Copy
            case ChangeType.DELETE => FileChangeType.Delete
            case ChangeType.MODIFY => FileChangeType.Modify
            case ChangeType.RENAME => FileChangeType.Rename
          }

          formatter.setContext(0)
          val edits = formatter.toFileHeader(entry).toEditList
          val (added, deleted) = edits.asScala.foldLeft((0, 0)) {
            case ((a, d), edit) =>
              (a + (edit.getEndB - edit.getBeginB), d + (edit.getEndA - edit.getBeginA))
          }

          FileChange(Paths.get(path), changeType, added, deleted)
      }
    } yield changedFiles.toSet
  }
}
