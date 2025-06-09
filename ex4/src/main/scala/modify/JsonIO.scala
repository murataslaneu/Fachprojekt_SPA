package modify

import modify.data.{AnalysisConfig, IgnoredCall, SelectedMethodsOfClass}
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, RTACallGraphKey, XTACallGraphKey}
import play.api.libs.json._

import java.io.File

/**
 * Helper object for JSON input/output operations.
 * Handles config and result file read/write as JSON.
 */
object JsonIO {
  /** Reads a json config file and returns the AnalysisConfig object.
   *
   * The json file may contain the following options:
   *   "projectJars",
   *   "libraryJars",
   *   "completelyLoadLibraries",
   *   "criticalMethods",
   *   "ignoreCalls",
   *   "entryPointsFinder",
   *   "customEntryPoints",
   *   "callGraphAlgorithm",
   *   "outputJson".
   */
  def readConfig(path: String): AnalysisConfig = {
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
      val projectFile = new File(path)
      if (!projectFile.exists) throw new java.io.IOException(
        s"Error in projectJars: Path $path could not be read from or does not exist."
      )
      projectFile
    }

    // libraryJars: List[String]
    // - Optional, but when given must contain valid paths.
    // - Defaults to an empty list.
    val libraryJarPaths = {
      val result = json \ "libraryJars"
      if (result.isDefined) result.get.as[List[String]]
      else List.empty[String]
    }
    val libraryJarFiles = libraryJarPaths.map { path =>
      val libraryFile = new File(path)
      if (!libraryFile.exists) throw new java.io.IOException(
        s"Error in libraryJars: Path $path could not be read from or does not exist."
      )
      libraryFile
    }

    // completelyLoadLibraries: Boolean
    // - Optional, must be true or false.
    // - Defaults to false.
    val completelyLoadLibraries = {
      val result = json \ "completelyLoadLibraries"
      if (result.isDefined) result.get.as[Boolean]
      else false
    }

    // criticalMethods: List[{"className": String, "methods": List[String]}]
    // - Optional, no checks on whether class/method names are valid.
    // - Defaults to methods getSecurityManager and setSecurityManager of java.lang.System
    val criticalMethods = {
      val result = json \ "criticalMethods"
      if (result.isDefined) {
        val validation: JsResult[List[SelectedMethodsOfClass]] = result.validate[List[SelectedMethodsOfClass]]
        validation match {
          case JsSuccess(criticalMethods, _) => criticalMethods
          case JsError(err) => throw new IllegalArgumentException(
            s"Error in criticalMethods: $err"
          )
        }
      }
      else List(SelectedMethodsOfClass("java.lang.System", List("getSecurityManager", "setSecurityManager")))
    }

    // ignoreCalls: List[{"callerClass": String, "callerMethod": String, "targetClass": String, "targetMethod": String}]
    // - Optional, no checks on whether class/method names are valid.
    // - Defaults to an empty list.
    val ignoreCalls = {
      val result = json \ "ignoreCalls"
      if (result.isDefined) {
        val validation: JsResult[List[IgnoredCall]] = result.validate[List[IgnoredCall]]
        validation match {
          case JsSuccess(ignoreCalls, _) => ignoreCalls
          case JsError(err) => throw new IllegalArgumentException(
            s"Error in ignoreCalls: $err"
          )
        }
      }
      else List.empty[IgnoredCall]
    }

    // entryPointsFinder: String
    // - Optional, user can choose between "custom", "application", "applicationWithJre", "library".
    // - Uppercase or lowercase irrelevant.
    // - Defaults to "application".
    val entryPointsFinder = {
      val result = json \ "entryPointsFinder"
      if (result.isDefined) {
        val mode = result.get.as[String].toLowerCase
        mode match {
          case s if s == "custom" => ("org.opalj.br.analyses.cg.ConfigurationEntryPointsFinder",
            "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder")
          case s if s == "application" => ("org.opalj.br.analyses.cg.ApplicationWithoutJREEntryPointsFinder",
            "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder")
          case s if s == "applicationwithjre" => ("org.opalj.br.analyses.cg.ApplicationEntryPointsFinder",
            "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder")
          case s if s == "library" => ("org.opalj.br.analyses.cg.LibraryEntryPointsFinder",
            "org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
          case invalid => throw new IllegalArgumentException(
            s"Error in entryPointsFinder: $invalid is not a valid finder."
          )
        }
      }
      else ("org.opalj.br.analyses.cg.ApplicationWithoutJREEntryPointsFinder",
        "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder")
    }

    // customEntryPoints: List[{className: String, methods: List[String]}]
    // - Same syntax as for criticalMethods.
    // - Optional, no checks on whether class/method names are valid.
    // - Defaults to an empty list.
    val customEntryPoints = {
      val result = json \ "customEntryPoints"
      if (result.isDefined) {
        result.validate[List[SelectedMethodsOfClass]] match {
          case JsSuccess(entryPoints, _) => entryPoints
          case JsError(err) => throw new IllegalArgumentException(
            s"Error in customEntryPoints: $err"
          )
        }
      }
      else List.empty[SelectedMethodsOfClass]
    }
    // For the entry points, the fully qualified names of the class must contain slashes instead of dots.
    // Example: "org.example.SomeClass" would be invalid here, but "org/example/SomeClass" is.
    // Converting it in code makes it more consistent for the user to input class names.
    customEntryPoints.foreach { entryPoint =>
      entryPoint.className = entryPoint.className.replace(".", "/")
    }

    // callGraphAlgorithm: String
    // - Optional, user can choose between "cha", "rta", "xta" and "1-1-cfa".
    // - Uppercase or lowercase irrelevant, multiple version for 1-1-cfa possible.
    // - Defaults to RTA.
    val callGraphAlgorithm = {
      val result = json \ "callGraphAlgorithm"
      if (result.isDefined) result.get.as[String].toLowerCase match {
        case "cha" => CHACallGraphKey
        case "rta" => RTACallGraphKey
        case "xta" => XTACallGraphKey
        case "cfa" => CFA_1_1_CallGraphKey
        case "1-1-cfa" => CFA_1_1_CallGraphKey
        case "1_1_cfa" => CFA_1_1_CallGraphKey
        case "cfa_1_1" => CFA_1_1_CallGraphKey
        case "cfa-1-1" => CFA_1_1_CallGraphKey
        case invalid => throw new IllegalArgumentException(
          s"Error in callGraphAlgorithm: $invalid is not a valid call graph algorithm."
        )
      }
      else RTACallGraphKey
    }

    // outputJson: String
    // - Optional, no further checks on path (path may already exist and gets overridden!).
    // - Defaults to no output.
    val outputJson: Option[String] = {
      val result = json \ "outputJson"
      if (result.isDefined) {
        val path = result.get.as[String]
        if (path.endsWith(".json")) Some(path) else Some(path + ".json")
      }
      else None
    }

    AnalysisConfig(projectJarFiles,
      libraryJarFiles,
      completelyLoadLibraries,
      criticalMethods,
      ignoreCalls,
      entryPointsFinder,
      customEntryPoints,
      callGraphAlgorithm,
      outputJson
    )
  }

  // TODO
  /** Writes the analysis result to a file in JSON format */
//  def writeResult(result: TPLAnalysisResult, path: String): Unit = {
//    val writer = new PrintWriter(new File(path))
//    writer.write(Json.prettyPrint(Json.toJson(result)))
//    writer.close()
//  }
}