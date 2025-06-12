ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "ex4"
  )

libraryDependencies += "de.opal-project" % "framework_2.13" % "5.0.0"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.10.6"