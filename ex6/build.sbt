ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "ex6"
  )

libraryDependencies ++= Seq(
  "de.opal-project" % "framework_2.13" % "5.0.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  "com.typesafe.play" %% "play-json" % "2.10.6",
  "org.openjfx" % "javafx-controls" % "20",
  "org.openjfx" % "javafx-fxml" % "20",
  "org.scalatest" %% "scalatest" % "3.2.19" % "test"
)