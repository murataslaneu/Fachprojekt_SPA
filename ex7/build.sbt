import sbtassembly.MergeStrategy

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "ex7"
  )

// OPAL depends on scala-xml version 1.3.0, while plugin sbt-scoverage depends on version 2.3.0
// This forces the used version to be 1.3.0 to keep compatibility with OPAL
dependencyOverrides ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0",
  "org.slf4j" % "slf4j-api" % "2.0.17",
  "ch.qos.logback" % "logback-classic" % "1.5.18"
)

libraryDependencies ++= Seq(
  // OPAL for static analysis
  "de.opal-project" % "framework_2.13" % "5.0.0",
  // For interacting with json files
  "com.typesafe.play" %% "play-json" % "2.10.7",
  // For testing
  "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  // For logging
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  // For charts
  "org.knowm.xchart" % "xchart" % "3.8.8"
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.first
  case PathList("META-INF", xs @ _*)             => MergeStrategy.discard
  case path if path.endsWith("MANIFEST.MF") => MergeStrategy.discard
  case path if path.contains("module-info.class") => MergeStrategy.discard
  case path if path.endsWith("logback.xml") => MergeStrategy.first
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

Test / parallelExecution := true