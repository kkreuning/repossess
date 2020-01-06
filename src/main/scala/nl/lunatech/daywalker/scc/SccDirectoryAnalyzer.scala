package nl.lunatech.daywalker.scc

import com.github.tototoshi.csv.CSVReader
import java.nio.file.Path
import java.nio.file.Paths
import nl.lunatech.daywalker.DirectoryAnalyzer
import nl.lunatech.daywalker.SourceFileAnalysis
import scala.io.Source
import sys.process._
import zio.Task

class SccDirectoryAnalyzer extends DirectoryAnalyzer[Task] {
  def analyzeDirectory(dir: Path, languageFilter: String => Boolean = _ => true): Task[Seq[SourceFileAnalysis]] = {
    val raw = s"scc ${dir.toAbsolutePath} --by-file -f csv".!!
    val reader = CSVReader.open(Source.fromString(raw))

    Task {
      reader
        .iterator
        .collect {
          case language :: location :: filename :: linesCount :: blanksCount :: commentsCount :: codeCount :: _ :: Nil
              if languageFilter(language) &&
                linesCount.toIntOption.isDefined &&
                blanksCount.toIntOption.isDefined &&
                commentsCount.toIntOption.isDefined &&
                codeCount.toIntOption.isDefined =>
            SourceFileAnalysis(
              language,
              dir.relativize(Paths.get(location)),
              filename,
              linesCount.toInt,
              blanksCount.toInt,
              commentsCount.toInt,
              codeCount.toInt
            )
        }
        .toSeq
    }
  }
}
