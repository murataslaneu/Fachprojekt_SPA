package create

import create.data.{AnalysisConfig, SelectedMethodsOfClass}
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, RTACallGraphKey, XTACallGraphKey}
import play.api.libs.json._

import java.io.{File, FileNotFoundException, IOException}
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
   *  - "projectJars"
   *  - "libraryJars"
   *  - "tplJar"
   *  - "includeNonPublicMethods"
   *  - "entryPointsFinder"
   *  - "customEntryPoints"
   *  - "callGraphAlgorithm"
   *  - "outputClassFiles"
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

    // tplJar: String
    // Required! Checks with libraryJars whether the given path is valid. tplPath must be a copy from a path in
    // libraryJars, otherwise an exception will be thrown!
    val tplJar = {
      val result = json \ "tplJar"
      if (result.isDefined) result.get.as[String].replace('\\', '/')
      else throw new NoSuchElementException(
        "Error in tplName: Path of third party library jar missing of which the class files should be generated from."
      )
    }
    val tplFile = new File(tplJar)
    if (!tplFile.exists) throw new FileNotFoundException(
      "Error in tplName: Given path is invalid. The path must lead to a library given via libraryJars!"
    )
    val libraryTplFile = libraryJarFiles.collectFirst { case libFile if libFile.getPath == tplFile.getPath ||
      libFile.getAbsolutePath == tplFile.getAbsolutePath => libFile
    }
    if (libraryTplFile.isEmpty) throw new IllegalArgumentException(
      "Error in tplName: Given path does not lead to a path given via libraryJars!"
    )

    // includeNonPublicMethods: Boolean
    // - Optional, must be true or false.
    // - Defaults to true.
    val includeNonPublicMethods = {
      val result = json \ "includeNonPublicMethods"
      if (result.isDefined) result.get.as[Boolean]
      else true
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

    // outputClassFiles
    // - Optional
    // - Checks if parent directory exists and if given folder for given path is empty
    // - Defaults to folder "result" inside the current directory
    val outputClassFiles = {
      val result = json \ "outputClassFiles"
      if (result.isDefined) result.get.as[String].replace('\\', '/')
      else "result"
    }
    checkOutputClassFiles(outputClassFiles)

    AnalysisConfig(projectJarFiles,
      libraryJarFiles,
      libraryTplFile.get,
      includeNonPublicMethods,
      entryPointsFinder,
      customEntryPoints,
      callGraphAlgorithm,
      outputClassFiles
    )
  }

  /**
   * Function that checks for the given path if:
   *  - Parent folder exists   --> If not, throw exception
   *  - Result folder exists   --> If not, create the folder
   *  - Result folder is empty --> If not, delete contents after user's permission, otherwise continue analysis
   *
   * @param resultFolderPath The path where the class files should be saved in
   */
  def checkOutputClassFiles(resultFolderPath: String): Unit = {
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