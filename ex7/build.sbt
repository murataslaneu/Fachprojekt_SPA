ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "ex7"
  )

// OPAL depends on scala-xml version 1.3.0, while plugin sbt-scoverage depends on version 2.3.0
// This forces the used version to be 1.3.0 to keep compatibility with OPAL
dependencyOverrides += "org.scala-lang.modules" %% "scala-xml" % "1.3.0"

libraryDependencies ++= Seq(
  // OPAL for static analysis
  "de.opal-project" % "framework_2.13" % "5.0.0",
  // For interacting with json files
  "com.typesafe.play" %% "play-json" % "2.10.7",
  // For testing
  "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  // For logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.5.18"
)

// Parallel execution is not possible as multiple tests are accessing the global variables
// of ArchitectureValidator.
// This structure gets forced by the AnalysisApplication of OPAL and as a result cannot be fixed easily.
// Therefore, disable parallel execution entirely (tests get executed fast anyway...)
Test / parallelExecution := false