import analyses.E_DeadCodeDetector.helpers.DeadCodeAnalysis
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import util.{JsonIO, ProjectInitializer}

class TestDeadCodeAnalysis extends AnyFunSuite {

  private val logger: Logger = LoggerFactory.getLogger("TestDeadCodeAnalysis")

  private val jsonIO = new JsonIO()

  private val outputPath = "src/test/analysis"

  /** Base config used for this test as a JsObject */
  private val configJson = Json.obj(
    "projectJars" -> Json.arr(
      "src/test/5_testFiles/MinimalExample.jar"
    ),
    "libraryJars" -> Json.arr(),
    "resultsOutputPath" -> outputPath,
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
      "callGraphAlgorithmName" -> "DEFAULT",
      "entryPointsFinder" -> "DEFAULT",
      "customEntryPoints" -> "DEFAULT"
    ),
    "tplUsageAnalyzer" -> Json.obj(
      "execute" -> false,
      "countAllMethods" -> "DEFAULT",
      "callGraphAlgorithmName" -> "DEFAULT",
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
      "callGraphAlgorithmName" -> "DEFAULT",
      "entryPointsFinder" -> "DEFAULT",
      "customEntryPoints" -> "DEFAULT"
    ),
    "deadCodeDetector" -> Json.obj(
      "execute" -> false,
      "completelyLoadLibraries" -> false,
      "domains" -> "DEFAULT"
    ),
    "architectureValidator" -> Json.obj(
      "execute" -> false,
      "onlyMethodAndFieldAccesses" -> "DEFAULT",
      "defaultRule" -> "DEFAULT",
      "rules" -> "DEFAULT"
    )
  )

  private val config = jsonIO.readStaticAnalysisConfig(configJson, outputPath)

//  private val config: AnalysisConfig = AnalysisConfig(
//    projectJars = List(new File("src/test/testFiles/MinimalExample.jar")),
//    libraryJars = List.empty[File],
//    completelyLoadLibraries = false,
//    interactive = false,
//    outputJson = "src/test/testFiles/testResults.json"
//  )

  test("Analysis should execute with expected results") {

    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = config.projectJars,
      libcpFiles = config.libraryJars
    )

    val (multiDomainReport, _) = DeadCodeAnalysis.analyze(logger, project, config)
    // Read contents and check if they match the expected result
    // Check report
    assert(multiDomainReport.projectJars.sameElements(Array("src/test/5_testFiles/MinimalExample.jar")))
    assert(multiDomainReport.libraryJars.sameElements(Array[String]()))
    assert(multiDomainReport.methodsFound.length == 1)
    // Check (only) method
    val method = multiDomainReport.methodsFound.head
    assert(method.fullSignature == "void main(java.lang.String[])")
    assert(method.enclosingTypeName == "com.example.Main")
    assert(method.numberOfTotalInstructions == 13)
    assert(method.numberOfDeadInstructions == method.deadInstructions.length)
    assert(method.deadInstructions.nonEmpty)
    // Check if instructions (probably) have expected format
    assert(method.deadInstructions.forall(instruction => instruction.programCounter >= 0))
    assert(method.deadInstructions.forall(instruction => instruction.stringRepresentation.nonEmpty))
  }
}
