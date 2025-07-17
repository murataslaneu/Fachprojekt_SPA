package util

import configs.StaticAnalysisConfig
import play.api.libs.json.{JsValue, Json}

import java.io.{File, PrintWriter}

object JsonIO {

  // Folder "analysis" inside the current directory
  // where the results get written to by default
  val DEFAULT_OUTPUT_DIRECTORY: String = "analysis"

  // File "config.json" inside the current directory
  val DEFAULT_INPUT_JSON_PATH: String = "config.json"

  def writeDefaultJson(): Unit = {
    // Create default json
    val json: JsValue = Json.obj(
      "projectJars" -> Json.arr(), // TODO: Maybe add analysis application itself
      "libraryJars" -> Json.arr(), // TODO: Maybe add dependencies of analysis application itself
      "resultsOutputPath" -> DEFAULT_OUTPUT_DIRECTORY,
      "godClassDetector" -> Json.obj(
        "execute" -> false,
        "wmcThresh" -> "DEFAULT",
        "tccThresh" -> "DEFAULT",
        "atfdThresh" -> "DEFAULT",
        "nofThresh" -> "DEFAULT"
      ),
      "criticalMethodsDetector" -> Json.obj(
        "execute" -> false,
        "criticalMethods" -> "DEFAULT",
        "ignore" -> "DEFAULT",
        "callGraphAlgorithm" -> "DEFAULT",
        "entryPointsFinder" -> "DEFAULT",
        "customEntryPoints" -> "DEFAULT"
      ),
      "tplUsageAnalyzer" -> Json.obj(
        "execute" -> false,
        "countAllMethods" -> "DEFAULT",
        "callGraphAlgorithm" -> "DEFAULT",
        "entryPointsFinder" -> "DEFAULT",
        "customEntryPoints" -> "DEFAULT"
      ),
      "criticalMethodsRemover" -> Json.obj(
        "execute" -> false,
        "criticalMethods" -> "DEFAULT",
        "ignore" -> "DEFAULT"
      ),
      "tplMethodsRemover" -> Json.obj(
        "execute" -> false,
        "tplJar" -> "DEFAULT",
        "includeNonPublicMethods" -> "DEFAULT",
        "callGraphAlgorithm" -> "DEFAULT",
        "entryPointsFinder" -> "DEFAULT",
        "customEntryPoints" -> "DEFAULT"
      ),
      "deadCodeDetector" -> Json.obj(
        "execute" -> false,
        "completelyLoadLibraries" -> "DEFAULT"
      ),
      "architectureValidator" -> Json.obj(
        "execute" -> false,
        "onlyMethodAndFieldAccesses" -> "DEFAULT",
        "defaultRule" -> "DEFAULT",
        "rules" -> "DEFAULT"
      )
    )

    // Write default json
    val writer = new PrintWriter(new File(DEFAULT_INPUT_JSON_PATH))
    writer.write(Json.prettyPrint(json))
    writer.close()
  }

  /**
   * Reads the json file at the given path and also retrieves the output for the analysis.
   *
   * Output path needed due to the logger.
   * */
  def readAnalysisConfigInit(configPath: String): (String, JsValue) = {
    val source = scala.io.Source.fromFile(configPath)
    val json = try Json.parse(source.mkString) finally source.close()

    val outputPath = {
      val result = json \ "resultsOutputPath"
      if (result.isEmpty) throw new NoSuchElementException(
        "Json config missing parameter \"resultsOutputPath\"."
      )
      val path = result.get.as[String]
      if (path == "DEFAULT") DEFAULT_OUTPUT_DIRECTORY
      else path
    }

    (outputPath, json)
  }

  def readStaticAnalysisConfig(json: JsValue, outputPath: String): StaticAnalysisConfig = {

    /* Reading base analysis config */

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
    null
  }

}
