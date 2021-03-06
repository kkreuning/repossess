package nl.kkreuning.repossess

import java.nio.file.Path

trait SnapShotter[F[_]] {
  def takeDirectorySnapshot(directory: Path): F[DirectorySnapshot]

  def takeFileSnapshots(directory: Path): F[Set[FileSnapshot]]
}
