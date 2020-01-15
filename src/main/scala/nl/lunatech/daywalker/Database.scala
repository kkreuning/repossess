package nl.lunatech.daywalker

trait Database[F[_]] {
  def reset: F[Unit]

  def listHashes: F[Vector[String]]

  def persistCommit(commit: Commit): F[Unit]

  def persistFileSnapshots(hash: String, fileSnapshots: Set[FileSnapshot]): F[Unit]
}
