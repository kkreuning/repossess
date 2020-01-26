import Util._

ThisBuild / organization := "Repossess"
ThisBuild / scalaVersion := "2.13.1"
ThisBuild / version := "0.0.1"
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-language:_",
  "-Ywarn-unused:_",
  // "-Xfatal-warnings",
  "-Ymacro-annotations"
)
ThisBuild / autoStartServer := false
ThisBuild / includePluginResolvers := true
ThisBuild / turbo := true
ThisBuild / useSuperShell := false
ThisBuild / watchBeforeCommand := Watch.clearScreen
ThisBuild / watchTriggeredMessage := Watch.clearScreenOnTrigger
ThisBuild / watchForceTriggerOnAnyChange := true
ThisBuild / shellPrompt := { state =>
  s"${prompt(projectName(state))}> "
}
ThisBuild / watchStartMessage := {
  case (iteration, ProjectRef(build, projectName), commands) =>
    Some {
      s"""
         |~${commands.map(styled).mkString(";")}
         |Monitoring source files for ${prompt(projectName)}... (Press enter to interrupt)""".stripMargin
    }
}
ThisBuild / resolvers += Resolver.jcenterRepo

Test / parallelExecution := false
Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oSD")
Test / turbo := true

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = project
  .in(file("."))
  .settings(
    name := "Repossess",
    libraryDependencies ++= Dependencies.Compile,
    libraryDependencies ++= Dependencies.Test,
    libraryDependencies ++= Seq(
      compilerPlugin(Dependencies.CompilerPlugins.`better-monadic-for`),
      crossPlugin(Dependencies.CompilerPlugins.`kind-projector`),
      crossPlugin(Dependencies.CompilerPlugins.`scala-typed-holes`)
    ),
    Compile / console / scalacOptions --= Seq(
      "-Ywarn-unused:_",
      "-Xfatal-warnings"
    ),
    Compile / compileIncremental / scalacOptions := (Compile / console / scalacOptions).value,
    Test / console / scalacOptions := (Compile / console / scalacOptions).value
  )

def crossPlugin(x: sbt.librarymanagement.ModuleID) = compilerPlugin(x cross CrossVersion.full)
