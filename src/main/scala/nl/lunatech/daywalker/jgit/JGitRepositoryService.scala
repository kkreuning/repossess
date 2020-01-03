package nl.lunatech.daywalker.jgit

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import nl.lunatech.daywalker.Author
import nl.lunatech.daywalker.Change
import nl.lunatech.daywalker.Commit
import nl.lunatech.daywalker.Hash
import nl.lunatech.daywalker.Mutation
import nl.lunatech.daywalker.RepositoryService
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.Using

final class JGitRepositoryService(val repositoryDir: Path, val branchName: String) extends RepositoryService {
  val git: Git = Git.open(new java.io.File(repositoryDir.toUri))
  val repository: Repository = git.getRepository()

  def listCommitHashes: Seq[Hash] = _listCommitsHashes(None)

  def listCommitHashes(offset: Hash): Seq[Hash] = _listCommitsHashes(Some(offset))

  private def _listCommitsHashes(maybeOffset: Option[Hash]) = {
    val branch = repository.resolve(branchName)
    val call =
      maybeOffset.fold(git.log.add(branch))(offset => git.log.addRange(repository.resolve(offset.value), branch))
    val commits = call.call().asScala.toList.reverse
    commits.map(commit => Hash(commit.getId.getName))
  }

  def parseCommit(commit: Hash): Option[Commit] = {
    Try {
      Using.resource(new RevWalk(repository)) { walk =>
        val revCommit = walk.parseCommit(repository.resolve(commit.value))
        val currentHash = Hash(revCommit.getId.getName)
        val parentHash = Option.when(revCommit.getParentCount > 0)(revCommit.getParent(0).getName).map(Hash(_))

        Commit(
          hash = currentHash,
          parentHash = parentHash,
          date = LocalDateTime.ofInstant(Instant.ofEpochMilli(revCommit.getCommitTime * 1000), ZoneId.systemDefault),
          author = Author(
            name = revCommit.getAuthorIdent.getName,
            emailAddress = revCommit.getAuthorIdent.getEmailAddress
          ),
          message = revCommit.getShortMessage,
          changes = parentHash.fold[Seq[Change]](Nil)(parentHash => diffCommits(parentHash, currentHash))
        )
      }
    }.toOption
  }

  def diffCommits(oldHash: Hash, newHash: Hash): Seq[Change] = {
    Try {
      val reader = repository.newObjectReader()
      val oldTree = new CanonicalTreeParser(null, reader, repository.resolve(s"${oldHash.value}^{tree}"))
      val newTree = new CanonicalTreeParser(null, reader, repository.resolve(s"${newHash.value}^{tree}"))
      val entries = git.diff().setOldTree(oldTree).setNewTree(newTree).call().asScala
      entries
    }.toOption
      .fold[Seq[Change]] {
        Nil
      } { entries =>
        entries.map { entry =>
          Change(
            mutation = entry.getChangeType match {
              case ChangeType.ADD    => Mutation.Add
              case ChangeType.COPY   => Mutation.Copy
              case ChangeType.DELETE => Mutation.Delete
              case ChangeType.MODIFY => Mutation.Modify
              case ChangeType.RENAME => Mutation.Rename
            },
            path = Paths.get(entry.getNewPath)
          )
        }.toSeq
      }
  }

  def checkout(commit: Hash): Unit = {
    git.checkout().setStartPoint(commit.value).call()
  }
}
