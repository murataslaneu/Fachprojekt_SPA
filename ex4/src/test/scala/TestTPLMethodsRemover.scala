import com.typesafe.config.{Config, ConfigFactory}
import create.FileIO
import create.data.{AnalysisConfig, SelectedMethodsOfClass}
import org.opalj.br.analyses.{ProgressManagement, Project}
import org.scalatest.funsuite.AnyFunSuite
import org.opalj.log.{DevNullLogger, GlobalLogContext, OPALLogger}
import org.opalj.tac.cg.XTACallGraphKey

import java.io.{File, IOException}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import scala.jdk.CollectionConverters.ListHasAsScala

class TestTPLMethodsRemover extends AnyFunSuite {

  /* Initializations */
  val projectFile = new File("src/test/4.1.2_testFiles/Test_ExampleProject.jar")
  val libraryFile1 = new File("src/test/4.1.2_testFiles/Test_ExampleLib.jar")
  val libraryFile2 = new File("src/test/4.1.2_testFiles/Test_UnusedLib.jar")

  // Mute output from OPAL
  // As this test is only loading a small project, the console output is not really needed
  OPALLogger.updateLogger(GlobalLogContext, DevNullLogger)

  // If there are remnants of a previous test run (e.g. when a test fails), delete folder
  if (new File("src/test/4.1.2_testFiles/results").exists) {
    println("Preparing test environment...")
    cleanUpResultsFolder()
  }


  //TPLMethodsRemover.config = Some(FileIO.readJsonConfig("src/test/4.1.2_testFiles/testConfig.json"))

  test("Loading config from json file gives expected results") {
    println("Loading from config file...")
    val objectConfig = FileIO.readJsonConfig("src/test/4.1.2_testFiles/testConfig.json")
    println("Testing if config received correct values...")
    testJsonConfigLoadedProperly(objectConfig)
    println("Config was loaded correctly.")
  }

  test("First analysis run (public methods + checking project config)") {
    println("Starting first analysis run (public methods only)")

    println("Setting up project...")
    setAnalysisConfig(includeNonPublicMethods = false)
    val project = TPLMethodsRemover.setupProject(
      Iterable.empty[File],
      Iterable.empty[File],
      completelyLoadLibraries = true,
      ConfigFactory.load
    )(GlobalLogContext)

    println("Checking if project config received the correct values...")
    testProjectConfigSetProperly(project.config)
    println("Project config set properly.")

    println("Analyze with created project...")
    TPLMethodsRemover.analyze(project, Seq.empty[String], ProgressManagement.None)
    println("Analysis completed.")

    println("Reading created class file(s)...")
    val classFile = new File("src/test/4.1.2_testFiles/results/lib/ExampleLibrary_412.class")
    assert(classFile.exists)
    val unusedLibFile = new File("src/test/4.1.2_testFiles/results/lib/ExampleUnusedLib_412.class")
    assert(!unusedLibFile.exists)

    println("Testing if class file contains exactly the used public library methods")
    val classFileProject = Project(classFile)
    assert(classFileProject.classFilesCount == 1)
    val projectClassFile = classFileProject.allClassFiles.head
    val methodNames = projectClassFile.methods.map { method => method.name }
    val expectedMethodNames = Array("delegateMethod", "secondMethod")
    assert(expectedMethodNames.forall { name => methodNames.contains(name) })
    val unexpectedMethodNames = Array("getLibraryInt", "unusedPublicMethod", "unusedPrivateMethod", "protectedMethod")
    assert(!unexpectedMethodNames.exists { name => methodNames.contains(name) })

    println("First analysis run finished. Deleting generated results...")
    cleanUpResultsFolder()
    println("Deletion finished successfully.")
  }

  test("Second analysis run (including non-public methods)") {
    println("Starting second analysis run (including non-public methods)")

    println("Setting up project...")
    setAnalysisConfig(includeNonPublicMethods = true)
    val project = TPLMethodsRemover.setupProject(
      Iterable.empty[File],
      Iterable.empty[File],
      completelyLoadLibraries = true,
      ConfigFactory.load
    )(GlobalLogContext)

    println("Analyze with created project...")
    TPLMethodsRemover.analyze(project, Seq.empty[String], ProgressManagement.None)
    println("Analysis completed.")

    println("Reading created class file(s)...")
    val classFile = new File("src/test/4.1.2_testFiles/results/lib/ExampleLibrary_412.class")
    assert(classFile.exists)
    val unusedLibFile = new File("src/test/4.1.2_testFiles/results/lib/ExampleUnusedLib_412.class")
    assert(!unusedLibFile.exists)

    println("Testing if class file contains only the used library methods (including non-public ones called indirectly)...")
    val classFileProject = Project(classFile)
    assert(classFileProject.classFilesCount == 1)
    val projectClassFile = classFileProject.allClassFiles.head
    val methodNames = projectClassFile.methods.map { method => method.name }
    val expectedMethodNames = Array("delegateMethod", "secondMethod", "getLibraryInt", "protectedMethod")
    assert(expectedMethodNames.forall { name => methodNames.contains(name) })
    val unexpectedMethodNames = Array("unusedPublicMethod", "unusedPrivateMethod")
    assert(!unexpectedMethodNames.exists { name => methodNames.contains(name) })

    println("Second analysis run finished. Deleting generated results...")
    cleanUpResultsFolder()
    println("Deletion finished successfully.")
  }

  /* Helper methods for the tests */

  /** Helper method testing if FileIO.readConfig reads the correct values from the json file */
  private def testJsonConfigLoadedProperly(objectConfig: AnalysisConfig): Unit = {
    assert(objectConfig.projectJars == List(projectFile))
    assert(objectConfig.libraryJars ==  List(libraryFile1, libraryFile2))
    assert(objectConfig.tplJar == libraryFile1)
    assert(!objectConfig.includeNonPublicMethods)
    assert(objectConfig.entryPointsFinder == ("org.opalj.br.analyses.cg.ApplicationWithoutJREEntryPointsFinder",
      "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder"))
    assert(objectConfig.customEntryPoints == List(SelectedMethodsOfClass("some/nonExistentClass", List("onlyHereToTestReadingConfig"))))
    assert(objectConfig.callGraphAlgorithm == XTACallGraphKey)
    assert(objectConfig.outputClassFiles == "src/test/4.1.2_testFiles/results")
  }

  /** Helper method setting up the config of TPLMethodsRemover */
  private def setAnalysisConfig(includeNonPublicMethods: Boolean): Unit = {
    TPLMethodsRemover.config = Some(AnalysisConfig(
      projectJars = List(projectFile),
      libraryJars = List(libraryFile1, libraryFile2),
      tplJar = libraryFile1,
      includeNonPublicMethods = includeNonPublicMethods,
      entryPointsFinder = ("org.opalj.br.analyses.cg.ApplicationWithoutJREEntryPointsFinder",
        "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder"),
      // Class names contain slashes here as this is required for the project config
      customEntryPoints = List(SelectedMethodsOfClass("some/nonExistentClass", List("onlyHereToTestReadingConfig"))),
      callGraphAlgorithm = XTACallGraphKey,
      outputClassFiles = "src/test/4.1.2_testFiles/results"
    ))
  }

  /** Helper method testing if the project config received the correct values */
  private def testProjectConfigSetProperly(projectConfig: Config): Unit = {
    val customEntryPoints = List(SelectedMethodsOfClass("some/nonExistentClass", List("onlyHereToTestReadingConfig")))
    assert(projectConfig.getString("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis") ==
      "org.opalj.br.analyses.cg.ApplicationWithoutJREEntryPointsFinder")
    assert(projectConfig.getString("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis") ==
      "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder")
    val entryPointsList = projectConfig.getConfigList("org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints").asScala
    entryPointsList.foreach { entryPointConfig =>
      assert(entryPointConfig.getString("declaringClass") == customEntryPoints.head.className)
      assert(entryPointConfig.getString("name") == customEntryPoints.head.methods.head)
    }
  }

  /* Helper method for cleanup after tests  */

  /** Helper method to automatically delete the files created in the results folder */
  private def cleanUpResultsFolder(): Unit = {
    // Change path on your own risk! Here, the tests will NOT ask you before deleting the contents of the folder!
    val pathObject = Path.of("src/test/4.1.2_testFiles/results").toAbsolutePath
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
