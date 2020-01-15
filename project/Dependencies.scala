import sbt._

object Dependencies {
  val Compile = Seq(
    "ch.qos.logback"             % "logback-classic"      % "1.2.3",
    "com.chuusai"                %% "shapeless"           % "2.3.3",
    "com.github.pureconfig"      %% "pureconfig-core"     % "0.12.1",
    "com.github.pureconfig"      %% "pureconfig-generic"  % "0.12.1",
    "com.github.tototoshi"       %% "scala-csv"           % "1.3.6",
    "com.h2database"             % "h2"                   % "1.4.200",
    "com.typesafe.scala-logging" %% "scala-logging"       % "3.9.2",
    "dev.zio"                    %% "zio"                 % "1.0.0-RC17",
    "dev.zio"                    %% "zio-interop-cats"    % "2.0.0.0-RC10",
    "eu.timepit"                 %% "refined"             % "0.9.10",
    "eu.timepit"                 %% "refined-pureconfig"  % "0.9.10",
    "io.chrisdavenport"          %% "log4cats-noop"       % "1.0.1",
    "io.github.jmcardon"         %% "tsec-common"         % "0.2.0-M1",
    "io.github.jmcardon"         %% "tsec-hash-jca"       % "0.2.0-M1",
    "org.apache.kafka"           % "kafka-clients"        % "2.0.0",
    "org.eclipse.jgit"           % "org.eclipse.jgit"     % "5.6.0.201912101111-r",
    "org.tpolecat"               %% "doobie-core"         % "0.8.8",
    "org.tpolecat"               %% "doobie-hikari"       % "0.8.8",
    "org.typelevel"              %% "cats-core"           % "2.1.0",
    "org.typelevel"              %% "cats-effect"         % "2.0.0",
    "org.typelevel"              %% "cats-laws"           % "2.1.0",
    "org.typelevel"              %% "cats-mtl-core"       % "0.7.0",
    "org.typelevel"              %% "cats-tagless-macros" % "0.10",
    "org.xerial"                 % "sqlite-jdbc"          % "3.30.1",
  )

  val Test = Seq(
    "com.tngtech.archunit" % "archunit"                % "0.12.0",
    "org.scalacheck"       %% "scalacheck"             % "1.14.2",
    "org.scalatest"        %% "scalatest"              % "3.1.0",
    "org.typelevel"        %% "cats-effect-laws"       % "2.0.0",
    "org.typelevel"        %% "cats-testkit-scalatest" % "1.0.0-RC1",
  ).map(_ % sbt.Test)

  object CompilerPlugins {
    val `better-monadic-for` = "com.olegpy"      %% "better-monadic-for" % "0.3.1"
    val `kind-projector` = "org.typelevel"       % "kind-projector"      % "0.11.0"
    val `scala-typed-holes` = "com.github.cb372" % "scala-typed-holes"   % "0.1.1"
  }
}
