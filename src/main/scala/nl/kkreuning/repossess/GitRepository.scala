package nl.kkreuning.repossess

trait GitRepository[F[_]] {
  def getCurrentHash: F[String]

  def checkout(hash: String): F[Unit]

  def listHashes(branch: String): F[Vector[String]]

  def getCommits(hashes: Seq[String]): F[Vector[Commit]]

  def listChangedFiles(newHash: String, maybeOldHash: Option[String]): F[Set[FileChange]]
}
