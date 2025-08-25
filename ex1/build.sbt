ThisBuild / version := "1.0.0"

ThisBuild / scalaVersion := "2.13.12"

libraryDependencies ++= Seq("de.opal-project" % "framework_2.13" % "5.0.0" withSources() withJavadoc())

lazy val root = (project in file("."))
  .settings(
    name := "God Class Detector"
  )
