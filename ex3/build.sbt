ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "ex3"
  )

libraryDependencies += "de.opal-project" % "framework_2.13" % "5.0.0"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.10.6"

//For graph
libraryDependencies += "com.lihaoyi" %% "ujson" % "3.2.0"
libraryDependencies += "org.knowm.xchart" % "xchart" % "3.8.7"

// https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.18"

// https://mvnrepository.com/artifact/com.google.code.gson/gson
libraryDependencies += "com.google.code.gson" % "gson" % "2.13.1"

// https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.19.0"

// https://mvnrepository.com/artifact/com.google.guava/guava
libraryDependencies += "com.google.guava" % "guava" % "33.4.8-jre"