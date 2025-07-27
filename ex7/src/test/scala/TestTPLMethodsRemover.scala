import analyses.D2_TPLMethodsRemover.TPLMethodsRemover
import org.opalj.br.analyses.Project
import org.opalj.log.{ConsoleOPALLogger, GlobalLogContext, OPALLogger}
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import util.JsonIO

import java.io.{File, IOException}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

class TestTPLMethodsRemover extends AnyFunSuite {

  /* Initializations */
  private val jsonIO = new JsonIO()
  private val logger: Logger = LoggerFactory.getLogger("TestTPLMethodsRemover")
  private val baseOutputPathString = "src/test/analysis_4.2"
  private def outputPathString(testNumber: Int): String = s"$baseOutputPathString.$testNumber"

  test("First analysis run (public methods)") {
    OPALLogger.updateLogger(GlobalLogContext, new ConsoleOPALLogger(ansiColored = false, minLogLevel = org.opalj.log.Error))
    logger.info("Starting first analysis run (public methods only)")

    logger.info("Setting up config...")
    val currentOutputPathString: String = outputPathString(1)
    val outputPath = Path.of(currentOutputPathString)
    val jsonConfig = Json.obj(
      "projectJars" -> Json.arr("src/test/4.1.2_testFiles/Test_ExampleProject.jar"),
      "libraryJars" -> Json.arr("src/test/4.1.2_testFiles/Test_ExampleLib.jar", "src/test/4.1.2_testFiles/Test_UnusedLib.jar"),
      "resultsOutputPath" -> currentOutputPathString,
      "tplMethodsRemover" -> Json.obj(
        "execute" -> true,
        "tplJar" -> "src/test/4.1.2_testFiles/Test_ExampleLib.jar",
        "includeNonPublicMethods" -> false,
        "callGraphAlgorithmName" -> "XTA",
        "entryPointsFinder" -> "application",
        "customEntryPoints" -> "DEFAULT"
      )
    )
    val config = jsonIO.readStaticAnalysisConfig(jsonConfig, currentOutputPathString)

    val analysisConfig = config.tplMethodsRemover

    val tplMethodsRemover = new TPLMethodsRemover(analysisConfig.execute)
    if(!Files.exists(outputPath)) {
      Files.createDirectory(outputPath)
    }
    tplMethodsRemover.executeAnalysis(config)

    logger.info("Reading created class file(s)...")
    val classFile = new File(s"$currentOutputPathString/4b_TPLMethodsRemover/tplDummy/lib/ExampleLibrary_412.class")
    assert(classFile.exists)
    val unusedLibFile = new File(s"$currentOutputPathString/4b_TPLMethodsRemover/tplDummy/lib/ExampleUnusedLib_412.class")
    assert(!unusedLibFile.exists)

    logger.info("Testing if class file contains exactly the used public library methods")
    val classFileProject = Project(classFile)
    assert(classFileProject.classFilesCount == 1)
    val projectClassFile = classFileProject.allClassFiles.head
    val methodNames = projectClassFile.methods.map { method => method.name }
    val expectedMethodNames = Array("delegateMethod", "secondMethod")
    assert(expectedMethodNames.forall { name => methodNames.contains(name) })
    val unexpectedMethodNames = Array("getLibraryInt", "unusedPublicMethod", "unusedPrivateMethod", "protectedMethod")
    assert(!unexpectedMethodNames.exists { name => methodNames.contains(name) })

    logger.info("First analysis run finished. Deleting generated results...")
    cleanUpResultsFolder(1)
    logger.info("Deletion finished successfully.")
  }

  test("Second analysis run (including non-public methods)") {
    OPALLogger.updateLogger(GlobalLogContext, new ConsoleOPALLogger(ansiColored = false, minLogLevel = org.opalj.log.Error))
    logger.info("Starting second analysis run (including non-public methods)")

    logger.info("Setting up config...")
    val currentOutputPathString = outputPathString(2)
    val outputPath = Path.of(currentOutputPathString)
    val jsonConfig = Json.obj(
      "projectJars" -> Json.arr("src/test/4.1.2_testFiles/Test_ExampleProject.jar"),
      "libraryJars" -> Json.arr("src/test/4.1.2_testFiles/Test_ExampleLib.jar", "src/test/4.1.2_testFiles/Test_UnusedLib.jar"),
      "resultsOutputPath" -> currentOutputPathString,
      "tplMethodsRemover" -> Json.obj(
        "execute" -> true,
        "tplJar" -> "src/test/4.1.2_testFiles/Test_ExampleLib.jar",
        "includeNonPublicMethods" -> true,
        "callGraphAlgorithmName" -> "XTA",
        "entryPointsFinder" -> "application",
        "customEntryPoints" -> "DEFAULT"
      )
    )
    val config = jsonIO.readStaticAnalysisConfig(jsonConfig, currentOutputPathString)

    val analysisConfig = config.tplMethodsRemover

    val tplMethodsRemover = new TPLMethodsRemover(analysisConfig.execute)
    if(!Files.exists(outputPath)) {
      Files.createDirectory(outputPath)
    }

    tplMethodsRemover.executeAnalysis(config)

    logger.info("Reading created class file(s)...")
    val classFile = new File(s"$currentOutputPathString/4b_TPLMethodsRemover/tplDummy/lib/ExampleLibrary_412.class")
    assert(classFile.exists)
    val unusedLibFile = new File(s"$currentOutputPathString/4b_TPLMethodsRemover/tplDummy/lib/ExampleUnusedLib_412.class")
    assert(!unusedLibFile.exists)

    logger.info("Testing if class file contains only the used library methods (including non-public ones called indirectly)...")
    val classFileProject = Project(classFile)
    assert(classFileProject.classFilesCount == 1)
    val projectClassFile = classFileProject.allClassFiles.head
    val methodNames = projectClassFile.methods.map { method => method.name }
    val expectedMethodNames = Array("delegateMethod", "secondMethod", "getLibraryInt", "protectedMethod")
    assert(expectedMethodNames.forall { name => methodNames.contains(name) })
    val unexpectedMethodNames = Array("unusedPublicMethod", "unusedPrivateMethod")
    assert(!unexpectedMethodNames.exists { name => methodNames.contains(name) })

    logger.info("Second analysis run finished. Deleting generated results...")
    cleanUpResultsFolder(2)
    logger.info("Deletion finished successfully.")
  }

  /** Helper method to automatically delete the files created in the results folder */
  private def cleanUpResultsFolder(testNumber: Int): Unit = {
    // Change path on your own risk! Here, the tests will NOT ask you before deleting the contents of the folder!
    val pathObject = Path.of(outputPathString(testNumber)).toAbsolutePath
    Files.walkFileTree(pathObject, new SimpleFileVisitor[Path]() {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
        Files.delete(dir)
        FileVisitResult.CONTINUE
      }
    })
  }
}
