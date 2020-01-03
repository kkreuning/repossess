package nl.lunatech.daywalker.scc

import com.github.tototoshi.csv._
import java.nio.file.Path
import java.nio.file.Paths
import nl.lunatech.daywalker.FileAnalyser
import nl.lunatech.daywalker.FileAnalysis
import scala.io.Source
import sys.process._

final class SccFileAnalyser(val rootDirectory: Path, val languageFilter: String => Boolean) extends FileAnalyser {
  def analyse: Seq[FileAnalysis] = {
    val raw = s"scc ${rootDirectory.toAbsolutePath} --by-file -f csv".!!
    val reader = CSVReader.open(Source.fromString(raw))

    reader
      .iterator
      .collect {
        case List(language, location, filename, lines, blanks, comments, code, _)
            if languageFilter(language) &&
              lines.toIntOption.isDefined &&
              blanks.toIntOption.isDefined &&
              comments.toIntOption.isDefined &&
              code.toIntOption.isDefined =>
          FileAnalysis(language, Paths.get(location), filename, lines.toInt, blanks.toInt, comments.toInt, code.toInt)
      }
      .toSeq
  }
}
