import data.SelectedMethodsOfClass
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import util.{JsonIO, ProjectInitializer}

import java.io.File
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Testing whether the ProjectInitializer does what it is supposed to do,
 * especially regarding the project config
 */
class TestProjectInitializer extends AnyFunSuite{

  private val logger: Logger = LoggerFactory.getLogger("TestProjectInitializer")

  private val jsonIO = new JsonIO()

  test("Project initializer works on a project without specifying a config") {
    val projectJars: Array[File] = Array(new File("src/test/6_groundTruth/ExampleMain.jar"))
    val libraryJars: Array[File] = Array(
      new File("src/test/6_groundTruth/ExampleHelper.jar"),
      new File("src/test/6_groundTruth/ExampleDBAdapter.jar")
    )

    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = projectJars,
      libcpFiles = libraryJars,
      completelyLoadLibraries = true
    )

    assert(!project.libraryClassFilesAreInterfacesOnly)
    assert(project.allClassFiles.size == 20)
  }

  test("Project initializer works a project with specifying a config") {
    // Initialize config
    val outputPath = "src/test/analysis"
    val configJson = Json.obj(
      "projectJars" -> Json.arr("src/test/4.1.2_testFiles/Test_ExampleProject.jar"),
      "libraryJars" -> Json.arr("src/test/4.1.2_testFiles/Test_ExampleLib.jar", "src/test/4.1.2_testFiles/Test_UnusedLib.jar"),
      "resultsOutputPath" -> outputPath,
      "tplMethodsRemover" -> Json.obj(
        "execute" -> false,
        "tplJar" -> "DEFAULT",
        "includeNonPublicMethods" -> "DEFAULT",
        "callGraphAlgorithmName" -> "RTA",
        "entryPointsFinder" -> "library",
        "customEntryPoints" -> List(
          SelectedMethodsOfClass("some.nonExistentClass", List("onlyHereToTestReadingConfig"))
        )
      )
    )
    val config = jsonIO.readStaticAnalysisConfig(configJson, outputPath)

    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = config.projectJars,
      libcpFiles = config.libraryJars,
      configuredConfig = ProjectInitializer.setupOPALProjectConfig(config.tplMethodsRemover.entryPointsFinder, config.tplMethodsRemover.customEntryPoints)
    )

    assert(project.libraryClassFilesAreInterfacesOnly)
    val projectConfig = project.config

    val customEntryPoints = List(SelectedMethodsOfClass("some/nonExistentClass", List("onlyHereToTestReadingConfig")))
    assert(projectConfig.getString("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis") ==
      "org.opalj.br.analyses.cg.LibraryEntryPointsFinder")
    assert(projectConfig.getString("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis") ==
      "org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
    val entryPointsList = projectConfig.getConfigList("org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints").asScala
    entryPointsList.foreach { entryPointConfig =>
      assert(entryPointConfig.getString("declaringClass") == customEntryPoints.head.className)
      assert(entryPointConfig.getString("name") == customEntryPoints.head.methods.head)
    }
  }
}
