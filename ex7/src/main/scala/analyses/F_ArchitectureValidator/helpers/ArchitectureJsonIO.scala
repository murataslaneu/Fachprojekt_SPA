package analyses.F_ArchitectureValidator.helpers

import analyses.F_ArchitectureValidator.data.{ArchitectureConfig, ArchitectureReport, ArchitectureSpec}
import play.api.libs.json._

import java.io.{File, PrintWriter}
import scala.io.Source

object ArchitectureJsonIO {
  /**
   * Reads architecture configuration from JSON file.
   *
   * Possible options:
   * - projectJars
   * - libraryJars
   * - specificationsFile
   * - outputJson
   * - completelyLoadLibraries
   * - onlyMethodAndFieldAccesses
   */
  def readConfig(path: String): ArchitectureConfig = {
    val source = scala.io.Source.fromFile(path)
    val json = try Json.parse(source.mkString) finally source.close()

    // projectJars: List[String]
    // - Required, must contain valid paths!
    val projectJarPaths = (json \ "projectJars").as[List[String]]
    val projectJarFiles = projectJarPaths.map { path =>
      val file = new File(path.replace('\\', '/'))
      if (!file.exists) throw new java.io.IOException(s"Project JAR not found: $path")
      file
    }

    // libraryJars: List[String]
    // - Optional, defaults to empty list
    // - If given, the paths must be valid!
    val libraryJarPaths = (json \ "libraryJars").asOpt[List[String]].getOrElse(List.empty)
    val libraryJarFiles = libraryJarPaths.map { path =>
      val file = new File(path.replace('\\', '/'))
      if (!file.exists) throw new java.io.IOException(s"Library JAR not found: $path")
      file
    }

    // specificationFile: String (containing the path to the file)
    // - Required, must contain valid path and lead to a json file
    val specificationFile = (json \ "specificationFile").as[String]
    val specFile = new File(specificationFile.replace('\\', '/'))
    if (!specFile.exists()) throw new java.io.IOException(s"Specification file not found: $specificationFile")
    if (!specificationFile.toLowerCase.endsWith(".json")) {
      throw new IllegalArgumentException(s"Specification file does not lead to a json file: $specificationFile")
    }

    // outputJson: String
    // - Optional, defaults to "architecture-report.json"
    var outputJson = (json \ "outputJson").asOpt[String].getOrElse("architecture-report.json")
    if (!outputJson.toLowerCase.endsWith(".json")) outputJson += ".json"


    // completelyLoadLibraries: Boolean
    // - Optional, defaults to false
    val completelyLoadLibraries = (json \ "completelyLoadLibraries").asOpt[Boolean].getOrElse(false)

    // onlyMethodAndFieldAccesses: Boolean
    // - Optional, defaults to false
    val onlyMethodAndFieldAccesses = (json \ "onlyMethodAndFieldAccesses").asOpt[Boolean].getOrElse(false)

    ArchitectureConfig(
      projectJarFiles,
      libraryJarFiles,
      specificationFile,
      outputJson,
      completelyLoadLibraries,
      onlyMethodAndFieldAccesses
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

  /**
   * Reads architecture specification from JSON file
   */
  def readSpecification(specFile: String): ArchitectureSpec = {
    val source = Source.fromFile(specFile)
    val json = try Json.parse(source.mkString) finally source.close()
    json.as[ArchitectureSpec]
  }
}