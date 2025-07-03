package helpers

import data.{ArchitectureConfig, ArchitectureReport}
import play.api.libs.json._

import java.io.{File, PrintWriter}

object ArchitectureJsonIO {
  /**
   * Reads architecture configuration from JSON file
   */
  def readConfig(path: String): ArchitectureConfig = {
    val source = scala.io.Source.fromFile(path)
    val json = try Json.parse(source.mkString) finally source.close()

    val projectJarPaths = (json \ "projectJars").as[List[String]]
    val projectJarFiles = projectJarPaths.map { path =>
      val file = new File(path.replace('\\', '/'))
      if (!file.exists) throw new java.io.IOException(s"Project JAR not found: $path")
      file
    }

    val libraryJarPaths = (json \ "libraryJars").asOpt[List[String]].getOrElse(List.empty)
    val libraryJarFiles = libraryJarPaths.map { path =>
      val file = new File(path.replace('\\', '/'))
      if (!file.exists) throw new java.io.IOException(s"Library JAR not found: $path")
      file
    }

    val specificationFile = (json \ "specificationFile").as[String]
    val outputJson = (json \ "outputJson").asOpt[String].getOrElse("architecture-report.json")
    val completelyLoadLibraries = (json \ "completelyLoadLibraries").asOpt[Boolean].getOrElse(false)

    ArchitectureConfig(
      projectJarFiles,
      libraryJarFiles,
      specificationFile,
      outputJson,
      completelyLoadLibraries
    )
  }

  /**
   * Writes architecture report to JSON file
   */
  def writeReport(report: ArchitectureReport, path: String): Unit = {
    val json = Json.prettyPrint(Json.toJson(report))
    val writer = new PrintWriter(new File(path))
    writer.write(json)
    writer.close()
  }
}