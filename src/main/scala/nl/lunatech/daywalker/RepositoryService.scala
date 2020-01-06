package nl.lunatech.daywalker

import java.nio.file.Path

trait RepositoryService[F[_]] {
  val dir: Path
  val branch: String

  def listCommitHashes: F[Seq[Hash]]

  def listCommitHashes(offset: Hash): F[Seq[Hash]]

  def readCommit(commit: Hash): F[Option[Commit]]

  def diffCommits(oldHash: Hash, newHash: Hash): F[Seq[Change]]
}
