ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "ex7"
  )

libraryDependencies += "de.opal-project" % "framework_2.13" % "5.0.0"