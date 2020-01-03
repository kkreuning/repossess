package nl.lunatech.daywalker

import java.nio.file.Path

trait RepositoryService {
  val repositoryDir: Path
  val branchName: String

  def listCommitHashes: Seq[Hash]

  def listCommitHashes(offset: Hash): Seq[Hash]

  def parseCommit(commit: Hash): Option[Commit]

  def diffCommits(oldHash: Hash, newHash: Hash): Seq[Change]

  def checkout(commit: Hash): Unit
}
