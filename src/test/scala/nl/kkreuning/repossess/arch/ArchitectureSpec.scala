package nl.kkreuning.repossess.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.library.Architectures
import nl.kkreuning.repossess.Main
import org.scalatest.flatspec.AnyFlatSpec
import scala.jdk.CollectionConverters._

class ArchitectureSpec extends AnyFlatSpec {
  private val root = "nl.kkreuning.repossess"
  private val classesWithoutMain = new ClassFileImporter()
    .withImportOption(ExcludeTests)
    .withImportOption(ExcludeClass(Main.getClass))
    .importPackages(root)

  "implementation layers" should "not access other implementation layers" in {
    val packages = classesWithoutMain.asScala.toSeq.groupBy(_.getPackageName)
    val implementations = packages.removed(root).keys
    val layered = implementations.foldLeft(Architectures.layeredArchitecture()) {
      case (architecture, pkg0) =>
        val name = pkg0.split('.').last
        val pkg = pkg0 + ".."

        note(s"Defined layer: '$name' as package '$pkg'")

        architecture
          .layer(name)
          .definedBy(pkg)
          .whereLayer(name)
          .mayNotBeAccessedByAnyLayer()
    }

    layered.check(classesWithoutMain)
  }

  object ExcludeTests extends ImportOption {
    private val SbtPattern = ".*/target/.*/test-classes/.*".r

    def includes(location: Location): Boolean =
      !location.matches(SbtPattern.pattern)
  }

  final case class ExcludeClass(cls: Class[_]) extends ImportOption {
    def includes(location: Location): Boolean =
      !location.contains(cls.getName().replace('.', '/').stripSuffix("$"))
  }
}
