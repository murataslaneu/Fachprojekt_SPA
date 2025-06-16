package modify

import modify.data.{AnalysisConfig, AnalysisResult, IgnoredCall, SelectedMethodsOfClass}
import play.api.libs.json._

import java.io.{File, IOException, PrintWriter}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

/**
 * Helper object for JSON input/output operations.
 * Handles config and result file read/write as JSON.
 */
object FileIO {
  /** Reads a json config file and returns the AnalysisConfig object.
   *
   * The json file may contain the following options:
   *   "projectJars",
   *   "libraryJars",
   *   "completelyLoadLibraries",
   *   "criticalMethods",
   *   "ignoreCalls"
   *   "outputClassFiles",
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

    // outputClassFiles
    // - Optional, no further checks on path (path may already exist, containing files may be overriden!)
    // - Defaults to folder "result" inside the current directory
    val outputClassFiles = {
      val result = json \ "outputClassFiles"
      if (result.isDefined) result.get.as[String]
      else "result"
    }
    checkOutputClassFiles(outputClassFiles)

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
      outputClassFiles,
      outputJson
    )
  }

  // Writes a list of analysis results to a JSON file
  def writeResult(result: List[AnalysisResult], path: String): Unit = {
    val json = Json.prettyPrint(Json.toJson(result))
    val writer = new PrintWriter(new File(path))
    writer.write(json)
    writer.close()
  }

  // Helper method for reading JSON result files in tests
  def readJsonResult(path: String): List[AnalysisResult] = {
    val source = scala.io.Source.fromFile(path, "UTF-8")
    val content = try source.mkString finally source.close()
    val parsed = Json.parse(content).validate[List[AnalysisResult]]
    parsed.getOrElse(throw new IllegalArgumentException(s"Invalid JSON in $path"))
  }

  /**
   * Function that checks for the given path if:
   *  - Parent folder exists   --> If not, throw exception
   *  - Result folder exists   --> If not, create the folder
   *  - Result folder is empty --> If not, delete contents after user's permission, otherwise continue analysis
   *
   * @param resultFolderPath The path where the class files should be saved in
   */
  private def checkOutputClassFiles(resultFolderPath: String): Unit = {
    val pathObject = Path.of(resultFolderPath).toAbsolutePath
    // Check if parent directory of given folder path exists
    if (!Files.exists(pathObject.getParent)) throw new IllegalArgumentException(
      s"Error in outputClassFiles: Parent directory of path $resultFolderPath does not exist."
    )

    // Parent folder exists
    // Create folder in parent directory if it not already exists
    if(!Files.exists(pathObject)) {
      Files.createDirectory(pathObject)
      println(s"${Console.BLUE}Created folder ${pathObject.getFileName} in ${pathObject.getParent}${Console.RESET}")
    }
    else {
      // Result folder already exists
      // If data is inside the result folder, the data has to be deleted for the analysis
      // Reason: If you e.g. run the analysis for one project, and then for another project, then the files stay, which
      //         would get messy quite quickly
      // To prevent accidental data loss, it is checked whether the result folder path already contains files
      if (Files.list(pathObject).findFirst().isPresent) {
        // Folder contains files or folders!
        println(s"${Console.RED}\n#################### CAUTION ######################${Console.RESET}")
        println(s"${Console.RED}Folder for outputClassFiles is not empty! ${Console.RESET}")
        println(s"${Console.RED}Path: $pathObject ${Console.RESET}")
        println(s"${Console.RED_B}(!) For the readability of the results, this analysis will IRRECOVERABLY DELETE the contents of that folder (!)${Console.RESET}")
        println(s"${Console.RED}If you don't want to continue, you can stop the analysis.")
        println(s"${Console.RED}##################################################${Console.RESET}\n")
        println(s"${Console.RED}Continue? (y/n) ${Console.RESET}")
        val userInput = scala.io.StdIn.readLine(">>> ").toLowerCase.trim

        if (userInput == "y" || userInput == "yes") {
          println(s"${Console.BLUE}Deleting contents of the results folder $pathObject ... ${Console.RESET}")
          Files.walkFileTree(pathObject, new SimpleFileVisitor[Path]() {
            override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
              Files.delete(file)
              FileVisitResult.CONTINUE
            }

            override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
              // Result folder itself should not be deleted!
              if (dir != pathObject) {
                Files.delete(dir)
              }
              FileVisitResult.CONTINUE
            }
          })
          println(s"${Console.BLUE}Deletion finished successfully. ${Console.RESET}")
        }
        else {
          println(s"${Console.YELLOW}Cancelled deletion, terminating analysis.${Console.RESET}")
          System.exit(0)
        }
      }
      else {
        println(s"Folder $pathObject for outputClassFiles empty, continuing analysis...")
      }
    }
  }
}