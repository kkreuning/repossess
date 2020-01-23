package nl.lunatech.daywalker.scc

import com.github.tototoshi.csv.CSVReader
import java.nio.file.Path
import java.nio.file.Paths
import nl.lunatech.daywalker.DirectorySnapshot
import nl.lunatech.daywalker.FileSnapshot
import nl.lunatech.daywalker.SnapShotter
import scala.io.Source
import sys.process._
import zio.Task

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
    for {
      csv <- Task(s"scc ${directory.toAbsolutePath} --by-file -f csv".!!)
      lines <- Task(CSVReader.open(Source.fromString(csv))).map(_.iterator)
      snapshots = lines.collect {
        case language :: path :: fileName :: lines :: code :: comments :: blanks :: complexity :: Nil
            if languageFilter(language) &&
              lines.toIntOption.isDefined &&
              code.toIntOption.isDefined &&
              comments.toIntOption.isDefined &&
              blanks.toIntOption.isDefined &&
              complexity.toIntOption.isDefined =>
          val relativePath = directory.relativize(Paths.get(path))

          val (namespace, scope) = parseNamespaceAndScope(relativePath)

          FileSnapshot(
            language.toLowerCase,
            relativePath,
            fileName,
            namespace,
            scope,
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

  private def parseNamespaceAndScope(relativePath: Path): (Option[String], String) = {
    import SccSnapshotter._

    relativePath.toString match {
      case MavenLayoutPattern(scope, _, pkg, _, _) => (Some(pkg.replace("/", ".")), scope)
      case _                                       => (None, "")
    }
  }
}

object SccSnapshotter {
  val MavenLayoutPattern = "^.*src\\/(\\w+)\\/(\\w+)\\/([\\w|\\/]+)\\/(\\w+)\\.(\\w+)".r
}
