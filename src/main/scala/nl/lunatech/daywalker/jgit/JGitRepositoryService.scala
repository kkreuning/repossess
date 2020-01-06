package nl.lunatech.daywalker.jgit

import java.nio.file.{Path, Paths}
import java.time.{LocalDateTime, ZoneOffset}
import nl.lunatech.daywalker.Author
import nl.lunatech.daywalker.Change
import nl.lunatech.daywalker.Commit
import nl.lunatech.daywalker.RepositoryService
import nl.lunatech.daywalker.Hash
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import scala.jdk.CollectionConverters._
import zio.{Task, UIO}

class JGitRepositoryService(val dir: Path, val branch: String) extends RepositoryService[Task] {
  private val git = Git.open(dir.toFile)
  private val repo = git.getRepository

  def listCommitHashes: Task[Seq[Hash]] = _listCommitHashes(None)

  def listCommitHashes(offset: Hash): Task[Seq[Hash]] = _listCommitHashes(Some(offset))

  private def _listCommitHashes(offsetOption: Option[Hash]): Task[Seq[Hash]] = {
    for {
      br <- Task(repo.resolve(branch))
      log <- Task(offsetOption.fold(git.log.add(br))(offset => git.log.addRange(repo.resolve(offset.value), br)))
      commits <- Task(log.call().asScala)
      names = commits.map(_.getId.getName).toSeq.reverse
    } yield names.map(Hash(_))
  }

  def readCommit(commit: Hash): Task[Option[Commit]] = {
    def acquire = Task(new RevWalk(repo))
    def release(walk: RevWalk) = UIO(walk.close())
    def use(walk: RevWalk): Task[Option[Commit]] = {
      for {
        commit <- Task(walk.parseCommit(repo.resolve(commit.value)))
        currentHash = Hash(commit.getId.getName)
        parentHash = Option.when(commit.getParentCount > 0)(commit.getParent(0).getName).map(Hash(_))
        date = LocalDateTime.ofEpochSecond(commit.getCommitTime * 1000L, 0, ZoneOffset.UTC)
        author = commit.getAuthorIdent
        message = commit.getShortMessage
        changes <- parentHash.fold[Task[Seq[Change]]](UIO.succeed(Nil))(ph => diffCommits(ph, currentHash))
      } yield Commit(currentHash, parentHash, date, Author(author.getName, author.getEmailAddress), message, changes)
    }.option

    Task.bracket(acquire, release, use)
  }

  def diffCommits(oldHash: Hash, newHash: Hash): Task[Seq[Change]] = {
    for {
      reader <- Task(repo.newObjectReader)
      oldRevStr <- Task(repo.resolve(s"${oldHash.value}^{tree}"))
      oldTree <- Task(new CanonicalTreeParser(null, reader, oldRevStr))
      newRevStr <- Task(repo.resolve(s"${newHash.value}^{tree}"))
      newTree <- Task(new CanonicalTreeParser(null, reader, newRevStr))
      diff = git.diff.setOldTree(oldTree).setNewTree(newTree)
      entries <- Task(diff.call().asScala)
      changes <- Task.collectAll(entries.map { entry =>
        for {
          path <- Task(Paths.get(entry.getNewPath))
          change <- UIO(entry.getChangeType match {
            case ChangeType.ADD    => Change.Add(path)
            case ChangeType.COPY   => Change.Copy(path)
            case ChangeType.DELETE => Change.Delete(path)
            case ChangeType.MODIFY => Change.Modify(path)
            case ChangeType.RENAME => Change.Rename(path)
          })
        } yield change
      })
    } yield changes
  }
}
