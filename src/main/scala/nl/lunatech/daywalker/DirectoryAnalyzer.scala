package nl.lunatech.daywalker

import java.nio.file.Path

trait DirectoryAnalyzer[F[_]] {
  def analyzeDirectory(dir: Path, languageFilter: String => Boolean = _ => true): F[Seq[SourceFileAnalysis]]
}
