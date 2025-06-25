package helpers

import data.AnalysisConfig

import play.api.libs.json._

import java.io.File

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
    // - Required, must contain valid paths!
    val libraryJarPaths = {
      val result = json \ "libraryJars"
      if (result.isDefined) result.get.as[List[String]]
      else throw new NoSuchElementException(
        "Error in libraryJars: Library jar(s) missing. Analysis can't do anything, thus cancelling execution."
      )
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
    // - Required
    val interactive = {
      val result = json \ "interactive"
      if (result.isDefined) result.get.as[Boolean]
      else throw new NoSuchElementException(
        "Error in interactive: Missing. Please add \"true\" or \"false\" to the option")
    }

    // showResults: Boolean
    // - Optional, defaults to false
    val showResults = {
      val result = json \ "showResults"
      if (result.isDefined) result.get.as[Boolean]
      else false
    }

    AnalysisConfig(projectJarFiles,
      libraryJarFiles,
      completelyLoadLibraries,
      interactive,
      showResults
    )
  }
}