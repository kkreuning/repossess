package nl.kkreuning.repossess.jgit

import java.nio.file.Files
import java.nio.file.Path
import nl.kkreuning.repossess.Author
import scala.collection.mutable
import scala.io.Source

// TODO: Make MailMap into an F[_] user
final class MailMap(
    mailToMailMappings: Map[MailMap.Mail, MailMap.Mail],
    fullToFullMappings: Map[(MailMap.Name, MailMap.Mail), (MailMap.Name, MailMap.Mail)]
  ) {
  import MailMap.{Mail, Name}

  def canonicalize(author: Author): Author = {
    val (canonicalName, canonicalEmailAddress) = canonicalize(author.name, author.emailAddress)
    author.copy(name = canonicalName, emailAddress = canonicalEmailAddress)
  }

  def canonicalize(name: Name, emailAddress: Mail): (Name, Mail) = {
    val canonicalName =
      fullToFullMappings
        .get((name, emailAddress))
        .map(_._1)
        .getOrElse(name)

    val canonicalEmailAddress =
      fullToFullMappings
        .get((name, emailAddress))
        .map(_._2)
        .orElse(mailToMailMappings.get(emailAddress))
        .getOrElse(emailAddress)

    (canonicalName, canonicalEmailAddress)
  }
}

object MailMap {
  private type Name = String
  private type Mail = String

  private val MailToMailRe = "^<(\\S+)>\\s+<(\\S+)>$".r
  private val FullToFullRe = "^(\\S.*?)\\s+<(\\S+)>\\s+(\\S.*?)\\s+<(\\S+)>$".r

  def apply(directory: Path): MailMap = {
    val mailToMailMappings: mutable.Map[Mail, Mail] = mutable.Map.empty
    val fullToFullMappings: mutable.Map[(Name, Mail), (Name, Mail)] = mutable.Map.empty

    val location = directory.resolve(".mailmap")

    if (Files.exists(location)) {
      val source = Source.fromFile(location.toUri)
      val lines = source.getLines.toList
      lines.collect {
        case MailToMailRe(realMail, aliasMail) => mailToMailMappings.put(aliasMail, realMail)
        case FullToFullRe(realName, realMail, aliasName, aliasMail) =>
          fullToFullMappings.put((realName, realMail), (aliasName, aliasMail))
      }
      lines
    }

    new MailMap(mailToMailMappings.toMap, fullToFullMappings.toMap)
  }
}
