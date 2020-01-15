package nl.lunatech.daywalker.scc

import com.github.tototoshi.csv.CSVReader
import java.nio.file.Path
import java.nio.file.Paths
import nl.lunatech.daywalker.DirectorySnapshot
import nl.lunatech.daywalker.FileSnapshot
import nl.lunatech.daywalker.Scope
import nl.lunatech.daywalker.SnapShotter
import scala.io.Source
import sys.process._
import zio.Task
import scala.util.Try

class SccSnapshotter(languageFilter: String => Boolean = _ => true) extends SnapShotter[Task] {
  def takeDirectorySnapshot(directory: Path): Task[DirectorySnapshot] = {
    takeFileSnapshots(directory).map { fileSnapshots =>
      var totalFiles = 0
      var totalLines = 0
      var totalCodeLines = 0
      var totalCommentsLines = 0
      var totalBlankLines = 0

      fileSnapshots.foreach { fileSnapshot =>
        totalFiles += 1
        totalLines += fileSnapshot.allLines
        totalCodeLines += fileSnapshot.codeLines
        totalCommentsLines += fileSnapshot.commentLines
        totalBlankLines += fileSnapshot.blankLines
      }

      DirectorySnapshot(
        totalFiles,
        totalLines,
        totalCodeLines,
        totalCommentsLines,
        totalBlankLines,
      )
    }
  }

  def takeFileSnapshots(directory: Path): Task[Set[FileSnapshot]] = {
    val namespacePattern = "src\\/(test|main)\\/\\w+\\/(.+)\\/.+$".r
    for {
      csv <- Task(s"scc ${directory.toAbsolutePath} --by-file -f csv".!!)
      lines <- Task(CSVReader.open(Source.fromString(csv))).map(_.iterator)
      snapshots = lines.collect {
        case language :: location :: fileName :: lines :: code :: comments :: blanks :: complexity :: Nil
            if languageFilter(language) &&
              lines.toIntOption.isDefined &&
              code.toIntOption.isDefined &&
              comments.toIntOption.isDefined &&
              blanks.toIntOption.isDefined &&
              complexity.toIntOption.isDefined =>
          FileSnapshot(
            language,
            directory.relativize(Paths.get(location)),
            fileName,
            Try(namespacePattern.findAllIn(location).group(2))
              .toOption
              .map(_.replaceAll("/", ".")), // TODO: Better namespace determination, only works with Maven layouts atm.
            if (location.contains("src/test")) Scope.Test else Scope.Main, // TODO: Better scope determination
            0,
            0,
            lines.toInt,
            code.toInt,
            comments.toInt,
            blanks.toInt,
            complexity.toInt,
            false // FIXME: Diff with changed files in this commit, required extra Git fiddling
          )
      }
    } yield snapshots.toSet
  }
}
