package helpers

import data.{AnalysisConfig, DeadCodeReport}
import play.api.libs.json._

import java.io.{File, PrintWriter}

/**
 * Helper object for JSON input/output operations.
 * Handles config and result file read/write as JSON.
 */
object JsonIO {
  /**
   * Reads a json config file and returns the AnalysisConfig object.
   *
   * The json file may contain the following options:
   *  - "projectJars"
   *  - "libraryJars"
   *  - "completelyLoadLibraries"
   *  - "interactive"
   *  - "showResults"
   *  - "outputJson"
   *
   * @param path Path to the config json
   */
  def readJsonConfig(path: String): AnalysisConfig = {
    val source = scala.io.Source.fromFile(path)
    val json = try Json.parse(source.mkString) finally source.close()

    // projectJars: List[String]
    // - Required, must contain valid paths!
    val projectJarPaths = {
      val result = json \ "projectJars"
      if (result.isDefined) result.get.as[List[String]]
      else throw new NoSuchElementException("Error in projectJars: Project jar(s) missing.")
    }
    val projectJarFiles = projectJarPaths.map { path =>
      val projectFile = new File(path.replace('\\', '/'))
      if (!projectFile.exists) throw new java.io.IOException(
        s"Error in projectJars: Path $path could not be read from or does not exist."
      )
      projectFile
    }

    // libraryJars: List[String]
    // - Optional, defaults to empty list
    // - If given, the paths must be valid!
    val libraryJarPaths = {
      val result = json \ "libraryJars"
      if (result.isDefined) result.get.as[List[String]]
      else List.empty[String]
    }
    val libraryJarFiles = libraryJarPaths.map { path =>
      val libraryFile = new File(path.replace('\\', '/'))
      if (!libraryFile.exists) throw new java.io.IOException(
        s"Error in libraryJars: Path $path could not be read from or does not exist."
      )
      libraryFile
    }

    // completelyLoadLibraries: Boolean
    // - Optional, defaults to false
    val completelyLoadLibraries = {
      val result = json \ "completelyLoadLibraries"
      if (result.isDefined) result.get.as[Boolean]
      else false
    }

    // interactive: Boolean
    // - Optional, defaults to true
    val interactive = {
      val result = json \ "interactive"
      if (result.isDefined) result.get.as[Boolean]
      else true
    }

    // showResults: Boolean
    // - Optional, defaults to false
    val showResults = {
      val result = json \ "showResults"
      if (result.isDefined) result.get.as[Boolean]
      else false
    }

    // outputJson: String
    // - Optional, defaults to "result.json"
    // - No further checks o path (path may e.g. already exists and gets overridden!)
    val outputJson = {
      val result = json \ "outputJson"
      if (result.isDefined) {
        val path = result.get.as[String]
        if (path.endsWith(".json")) path else path + ".json"
      }
      else "result.json"
    }

    AnalysisConfig(projectJarFiles,
      libraryJarFiles,
      completelyLoadLibraries,
      interactive,
      showResults,
      outputJson
    )
  }

  /**
   * Writes generated report in json format.
   *
   * @param result The DeadCodeReport to write
   * @param path Path where the report should be written to
   * */
  def writeResult(result: DeadCodeReport, path: String): Unit = {
    val json = Json.prettyPrint(Json.toJson(result))
    val writer = new PrintWriter(new File(path))
    writer.write(json)
    writer.close()
  }
}