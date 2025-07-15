import com.typesafe.config.ConfigFactory
import data.AccessType.{FIELD_ACCESS, INTERFACE_IMPLEMENTATION, METHOD_CALL}
import data.{ArchitectureConfig, ArchitectureReport, ArchitectureSpec, Dependency, Rule}
import helpers.ArchitectureValidation
import org.opalj.br.analyses.ProgressManagement
import org.opalj.log.GlobalLogContext
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.Json

import java.io.File

/**
 * Tests for the analysis regarding the ground truth.
 */
class TestAnalysisWithGroundTruth extends AnyFunSuite {

  private val defaultConfig = ArchitectureConfig(
    projectJars = List(
      new File("ground_truth/ExampleMain.jar"),
      new File("ground_truth/ExampleHelper.jar"),
      new File("ground_truth/ExampleDBAdapter.jar")
    ),
    libraryJars = List.empty,
    specificationFile = "ground_truth/spec.json",
    outputJson = "ground_truth/architecture-report.json",
    onlyMethodAndFieldAccesses = true
  )


  private val defaultSpec = ArchitectureSpec(
    defaultRule = "ALLOWED",
    rules = List(
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
  )


  /**
   * First test: Simulating a full analysis run on the ground truth with the given config
   */
  test("Complete analysis run on ground truth (with given config file) working as expected") {
    // (Partly) simulate input via terminal
    val warnings = ArchitectureValidator.checkAnalysisSpecificParameters(
      Seq(
        "-config=ground_truth/config.json",
        "-spec=something.json",
        "-output=other.json",
        "-onlyMethodAndFieldAccesses",
        "-someUnknownParameter"
      )
    )

    // Checking if input got parsed correctly
    assert(warnings.size == 1)
    assert(warnings.iterator.next.contains("-someUnknownParameter"))
    assert(ArchitectureValidator.configFile.get == "ground_truth/config.json")
    assert(ArchitectureValidator.config.get == defaultConfig)
    assert(ArchitectureValidator.specFile.get == "something.json")
    assert(ArchitectureValidator.outputPath == "other.json")
    assert(ArchitectureValidator.onlyMethodAndFieldAccesses)

    // Setting up project for analysis
    // Expecting config to be loaded, not "terminal parameters"
    val project = ArchitectureValidator.setupProject(
      List(),
      List(),
      completelyLoadLibraries = false,
      ConfigFactory.load
    )(GlobalLogContext)

    // Rudimentary check if (probably) the correct project got loaded
    assert(project.classFilesCount == 20)

    // Executing analysis
    ArchitectureValidator.analyze(project, Seq(), ProgressManagement.None)

    // Check if report got written and read it if possible
    val reportFile = new File("ground_truth/architecture-report.json")
    assert(reportFile.exists())
    val source = scala.io.Source.fromFile(reportFile)
    val json = try Json.parse(source.mkString) finally source.close()
    val report = json.as[ArchitectureReport]

    // Check if results are as expected
    assert(report.filesAnalyzed.size == 3)
    assert(report.specificationFile == "ground_truth/spec.json")
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
    val testConfig = defaultConfig.copy(onlyMethodAndFieldAccesses = false)
    ArchitectureValidator.config = Some(testConfig)

    // Setting up project for analysis
    val project = ArchitectureValidator.setupProject(
      List(),
      List(),
      completelyLoadLibraries = false,
      ConfigFactory.load
    )(GlobalLogContext)

    // Execute analysis
    val report = ArchitectureValidation.analyze(project, defaultSpec, testConfig)

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
