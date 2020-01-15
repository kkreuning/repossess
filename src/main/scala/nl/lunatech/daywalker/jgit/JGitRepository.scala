package nl.lunatech.daywalker.jgit

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import nl.lunatech.daywalker.Author
import nl.lunatech.daywalker.Commit
import nl.lunatech.daywalker.FileChange
import nl.lunatech.daywalker.GitRepository
import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.util.io.NullOutputStream
import scala.jdk.CollectionConverters._
import zio.Task
import zio.UIO

class JGitRepository(directory: Path) extends GitRepository[Task] {
  private type Hash = String

  private val jgit = JGit.open(directory.toFile) // TODO: Make managed resource
  private val repo = Task(jgit.getRepository)

  val getCurrentHash: Task[Hash] = repo.map(_.getFullBranch)

  def checkout(hash: Hash): Task[Unit] = Task(jgit.checkout.setName(hash).call()).unit

  def listHashes(branch: String): Task[Vector[Hash]] =
    for {
      branch <- repo.map(_.resolve(branch))
      commits0 <- Task(jgit.log.add(branch).call)
      commits = commits0.asScala.toVector.reverse
      hashes = commits.map(_.getId.getName)
    } yield hashes

  def getCommits(hashes: Seq[Hash]): Task[Vector[Commit]] = {
    def acquire = repo.map(new RevWalk(_))
    def release(revWalk: RevWalk) = UIO(revWalk.close())
    def use(revWalk: RevWalk): Task[Vector[Commit]] = {
      val getTags = for {
        op <- UIO(jgit.tagList)
        refs <- Task(op.call().asScala)
        hashesWithNames = refs.map(ref => (ref.getObjectId.getName -> ref.getName.stripPrefix("refs/tags/")))
        map = hashesWithNames.toMap
      } yield map

      def parseCommit(hash: Hash, tag: Option[String]) =
        for {
          resolved <- repo.map(_.resolve(hash))
          revCommit <- Task(revWalk.parseCommit(resolved))
        } yield Commit(
          revCommit.getId.getName,
          Option.when(revCommit.getParentCount > 0)(revCommit.getParent(0).getName()),
          LocalDateTime.ofEpochSecond(revCommit.getCommitTime, 0, java.time.ZoneOffset.UTC),
          tag,
          Author(revCommit.getAuthorIdent.getName, revCommit.getAuthorIdent.getEmailAddress),
          revCommit.getShortMessage
        )

      for {
        tags <- getTags
        commits <- Task.collectAll(hashes.map(hash => parseCommit(hash, tags.get(hash))))
      } yield commits.toVector
    }

    Task.bracket(acquire, release, use)
  }

  def listChangedFiles(oldHash: String, newHash: String): Task[Set[FileChange]] = {
    for {
      reader <- repo.map(_.newObjectReader)
      oldRevStr <- repo.map(_.resolve(s"$oldHash^{tree}"))
      oldTree <- Task(new CanonicalTreeParser(null, reader, oldRevStr))
      newRevStr <- repo.map(_.resolve(s"$newHash^{tree}"))
      newTree <- Task(new CanonicalTreeParser(null, reader, newRevStr))
      out = NullOutputStream.INSTANCE
      formatter <- repo.map { r =>
        val df = new DiffFormatter(out); df.setRepository(r); df
      }
      diff = jgit.diff.setOldTree(oldTree).setNewTree(newTree)
      entries <- Task(diff.call.asScala)
      changedFiles <- Task(entries.collect {
        case entry if entry.getNewPath() != "/dev/null" => // TODO: Better src detection
          formatter.setContext(0)
          val editList = formatter.toFileHeader(entry).toEditList()

          val (added, deleted) = editList.asScala.foldLeft((0, 0)) {
            case ((a, d), edit) =>
              (
                a + (edit.getEndA - edit.getBeginA),
                d + (edit.getEndB - edit.getBeginB)
              )
          }

          FileChange(Paths.get(entry.getNewPath), added, deleted)
      })
    } yield changedFiles.toSet
  }
}
