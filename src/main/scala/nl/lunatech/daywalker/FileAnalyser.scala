package nl.lunatech.daywalker

import java.nio.file.Path

trait FileAnalyser {
  val rootDirectory: Path
  val languageFilter: String => Boolean

  def analyse: Seq[FileAnalysis]
}
