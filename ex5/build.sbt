ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "ex5"
  )

// OPAL depends on scala-xml version 1.3.0, while plugin sbt-scoverage depends on version 2.3.0
// This forces the used version to be 1.3.0 to keep compatibility with OPAL
dependencyOverrides += "org.scala-lang.modules" %% "scala-xml" % "1.3.0"

// For testing
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % "test"

libraryDependencies += "de.opal-project" % "framework_2.13" % "5.0.0"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.10.6"

libraryDependencies ++= Seq(
  "org.openjfx" % "javafx-controls" % "20",
  "org.openjfx" % "javafx-fxml" % "20"
)