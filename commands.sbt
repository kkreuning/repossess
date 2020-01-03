import Util._

addCommandAlias("update", "reload plugins; dependencyUpdates; reload return; dependencyUpdates")
addCommandAlias("lintDependencies", "reload; unusedCompileDependencies; undeclaredCompileDependencies")

onLoadMessage +=
  s"""
    |
    | List of common ${styled("commands")}:
    |┌──────────────────┬────────────────────────────────────────────┐
    |│ ${styled("run")}              │ run the application                        │
    |│ ${styled("test")}             │ run tests                                  │
    |├──────────────────┼────────────────────────────────────────────┤
    |│ ${styled("update")}           │ check for dependency updates               │
    |│ ${styled("lintDependencies")} │ check unused and undeclared dependencies   │
    |│ ${styled("scalafmt")}         │ format sources with scalafmt               │
    |└──────────────────┴────────────────────────────────────────────┘
  """.stripMargin
