import analyses.F_ArchitectureValidator.data.AccessType._
import analyses.F_ArchitectureValidator.data.{ArchitectureSpec, Dependency, Rule}
import analyses.F_ArchitectureValidator.helpers.ArchitectureValidation
import com.typesafe.scalalogging.Logger
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.Json
import util.{JsonIO, ProjectInitializer}


/**
 * Tests for the analysis regarding the ground truth.
 */
class TestArchitectureValidatorWithGroundTruth extends AnyFunSuite {

  private val logger = Logger("TestArchitectureValidatorWithGroundTruth")

  private val jsonIO = new JsonIO()

  private val outputPath = "src/test/analysis"

  private val defaultRule = "ALLOWED"

  private val rules = List(
    Rule("ExampleMain.jar", "ExampleDBAdapter.jar", "FORBIDDEN", Some(List(
      Rule("com.example.main", "com.example.db.DBConnection", "ALLOWED"),
      Rule("com.example.main", "com.example.db.DBTaskRepository", "ALLOWED"),
      Rule("com.example.main", "com.example.db.QueryBuilder", "ALLOWED"),
      Rule("com.example.main", "com.example.db.adapters.PersistentTask", "ALLOWED")
    ))),
    Rule("com.example.main.TaskFactory", "com.example.db.adapters", "FORBIDDEN"),
    Rule("com.example.main.impl.DefaultTask", "com.example.helper.api.Retryable", "FORBIDDEN"),
    Rule("ExampleHelper.jar", "ExampleDBAdapter.jar", "FORBIDDEN"),
    Rule("com.example.helper", "ExampleDBAdapter.jar", "FORBIDDEN"),
    Rule("com.example.db", "com.example.helper.Logger", "FORBIDDEN", Some(List(
      Rule("com.example.db.utils.DBLogger", "com.example.helper.Logger", "ALLOWED")
    ))),
    Rule("com.example.db.InMemoryDB", "com.example.helper", "FORBIDDEN"),
    Rule("com.example.db.adapters.PersistentTask", "com.example.helper.api.Retryable", "FORBIDDEN")
  )

  /** Base config used for this test as a JsObject */
  private val configJson = Json.obj(
    "projectJars" -> Json.arr(
      "src/test/6_groundTruth/ExampleMain.jar",
      "src/test/6_groundTruth/ExampleHelper.jar",
      "src/test/6_groundTruth/ExampleDBAdapter.jar"
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
      "completelyLoadLibraries" -> "DEFAULT",
      "domains" -> "DEFAULT"
    ),
    "architectureValidator" -> Json.obj(
      "execute" -> true,
      "onlyMethodAndFieldAccesses" -> true,
      "defaultRule" -> defaultRule,
      "rules" -> Json.toJson(rules)
    )
  )

  /** Config used for the analysis run that only looks at method at field accesses */
  private val configOnlyMethodAndFieldAccesses = jsonIO.readStaticAnalysisConfig(configJson, outputPath)

  /** Config used for the analysis run that looks at every possible dependency */
  private val configAllAccesses = jsonIO.readStaticAnalysisConfig(
    configJson + ("architectureValidator" -> Json.obj(
      "execute" -> true,
      "onlyMethodAndFieldAccesses" -> false,
      "defaultRule" -> defaultRule,
      "rules" -> Json.toJson(rules)
    )),
    outputPath
  )

  /**
   * First test: Analysis run only looking at method and field accesses
   */
  test("Analysis on ground truth considering only method and field accesses should work as expected") {
    // Setting up project for analysis
    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = configOnlyMethodAndFieldAccesses.projectJars,
      libcpFiles = configOnlyMethodAndFieldAccesses.libraryJars
    )

    // Rudimentary check if (probably) the correct project got loaded
    assert(project.classFilesCount == 20)

    // Executing analysis
    val report = ArchitectureValidation.analyze(
      logger = logger,
      project = project,
      specification = ArchitectureSpec(
        defaultRule = configOnlyMethodAndFieldAccesses.architectureValidator.defaultRule,
        rules = configOnlyMethodAndFieldAccesses.architectureValidator.rules
      ),
      config = configOnlyMethodAndFieldAccesses
    )

    // Check if results are as expected
    assert(report.filesAnalyzed.size == 3)
    assert(report.checkedOnlyMethodAndFieldAccesses)
    val violations = report.violations
    assert(violations.size == 4)
    testMethodAndFieldAccessViolations(violations)
    // Interface implementation dependency should not be contained
    // due to onlyMethodAndFieldAccesses == true
    assert(!checkInterfaceImplementationViolationExists(violations))
  }


  /**
   * Second test: Execute analysis on the same config as in the first test,
   * but with onlyMethodAndFieldAccesses = false.
   */
  test("Analysis on ground truth considering all dependencies should work as expected") {
    // Setting up project for analysis
    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = configOnlyMethodAndFieldAccesses.projectJars,
      libcpFiles = configOnlyMethodAndFieldAccesses.libraryJars
    )

    // Execute analysis
    val report =     ArchitectureValidation.analyze(
      logger = logger,
      project = project,
      specification = ArchitectureSpec(
        defaultRule = configAllAccesses.architectureValidator.defaultRule,
        rules = configAllAccesses.architectureValidator.rules
      ),
      config = configAllAccesses
    )

    // Test if analysis retrieves the expected results
    assert(report.filesAnalyzed.size == 3)
    assert(!report.checkedOnlyMethodAndFieldAccesses)

    val violations = report.violations
    assert(violations.size == 5)
    testMethodAndFieldAccessViolations(violations)
    assert(checkInterfaceImplementationViolationExists(violations))
  }


  /**
   * Asserts that the method and field access dependencies are contained inside the violations list.
   */
  private def testMethodAndFieldAccessViolations(violations: List[Dependency]): Unit = {
    assert(violations.contains(Dependency(
      fromClass = "DBTaskRepository",
      toClass = "Logger",
      fromPackage = "com.example.db",
      toPackage = "com.example.helper",
      fromJar = "ExampleDBAdapter.jar",
      toJar = "ExampleHelper.jar",
      accessType = METHOD_CALL)))
    assert(violations.contains(Dependency(
      fromClass = "Application",
      toClass = "InMemoryDB",
      fromPackage = "com.example.main",
      toPackage = "com.example.db",
      fromJar = "ExampleMain.jar",
      toJar = "ExampleDBAdapter.jar",
      accessType = METHOD_CALL
    )))
    assert(violations.contains(Dependency(
      fromClass = "Application",
      toClass = "DBLogger",
      fromPackage = "com.example.main",
      toPackage = "com.example.db.utils",
      fromJar = "ExampleMain.jar",
      toJar = "ExampleDBAdapter.jar",
      accessType = METHOD_CALL
    )))
    assert(violations.contains(Dependency(
      fromClass = "InMemoryDB",
      toClass = "StringUtils",
      fromPackage = "com.example.db",
      toPackage = "com.example.helper",
      fromJar = "ExampleDBAdapter.jar",
      toJar = "ExampleHelper.jar",
      accessType = FIELD_ACCESS
    )))
  }


  /**
   * Returns whether the violations contain the interface implementation dependency
   * from com.example.main.impl.DefaultTask
   * to com.example.helper.api.Retryable.
   */
  private def checkInterfaceImplementationViolationExists(violations: List[Dependency]): Boolean = {
    violations.contains(Dependency(
      fromClass = "DefaultTask",
      toClass = "Retryable",
      fromPackage = "com.example.main.impl",
      toPackage = "com.example.helper.api",
      fromJar = "ExampleMain.jar",
      toJar = "ExampleHelper.jar",
      accessType = INTERFACE_IMPLEMENTATION
    ))
  }
}
