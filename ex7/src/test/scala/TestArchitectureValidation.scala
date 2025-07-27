import org.scalatest.funsuite.AnyFunSuite
import analyses.F_ArchitectureValidator.data.AccessType._
import analyses.F_ArchitectureValidator.data.{ArchitectureSpec, Dependency, Rule}
import analyses.F_ArchitectureValidator.helpers.ArchitectureValidation
import org.opalj.log.{ConsoleOPALLogger, GlobalLogContext, OPALLogger}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import util.{JsonIO, ProjectInitializer}

/**
 * Some additional tests checking the functionality next to the TestArchitectureValidatorWithGroundTruth
 */
class TestArchitectureValidation extends AnyFunSuite {

  private val logger: Logger = LoggerFactory.getLogger("TestArchitectureValidation")

  private val jsonIO = new JsonIO()

  private val defaultRuleTest1 = "ALLOWED"

  private val rulesTest1 = List(
    Rule(from = "org.example.Main", to = "org.example.SomeClass", `type` = "FORBIDDEN", except = None),
    Rule(from = "org.example.AbstractClass", to = "org.example.Interface", `type` = "FORBIDDEN", except = None),
    Rule(from = "org.example.InheritingClass", to = "org.example.AbstractClass", `type` = "FORBIDDEN", except = None)
  )

  private val outputPath = "src/test/analysis"

  /** Base config used for this test as a JsObject */
  private val configJsonTest1 = Json.obj(
    "projectJars" -> Json.arr(
      "src/test/6_allDependenciesExample/AllDependenciesExample-1.0-SNAPSHOT.jar"
    ),
    "libraryJars" -> Json.arr(),
    "resultsOutputPath" -> outputPath,
    "architectureValidator" -> Json.obj(
      "execute" -> true,
      "onlyMethodAndFieldAccesses" -> false,
      "defaultRule" -> defaultRuleTest1,
      "rules" -> Json.toJson(rulesTest1)
    )
  )

  private val configTest1 = jsonIO.readStaticAnalysisConfig(configJsonTest1, outputPath)

  private val defaultRuleTest2 = "ALLOWED"

  private val rulesTest2 = List(
    Rule("ExampleMain.jar", "java.lang.System", "FORBIDDEN"),
    Rule("ExampleHelper.jar", "java.lang.System", "FORBIDDEN"),
    Rule("ExampleDBAdapter.jar", "java.lang.System", "FORBIDDEN")
  )

  /** Base config used for this test as a JsObject */
  private val configJsonTest2 = Json.obj(
    "projectJars" -> Json.arr(
      "src/test/6_groundTruth/ExampleMain.jar",
      "src/test/6_groundTruth/ExampleHelper.jar",
      "src/test/6_groundTruth/ExampleDBAdapter.jar"
    ),
    "libraryJars" -> Json.arr(),
    "resultsOutputPath" -> outputPath,
    "architectureValidator" -> Json.obj(
      "execute" -> true,
      "onlyMethodAndFieldAccesses" -> true,
      "defaultRule" -> defaultRuleTest2,
      "rules" -> Json.toJson(rulesTest2)
    )
  )

  private val configTest2 = jsonIO.readStaticAnalysisConfig(configJsonTest2, outputPath)

  test("Analysis run on example project with all possible dependencies should give expected results") {
    OPALLogger.updateLogger(GlobalLogContext, new ConsoleOPALLogger(ansiColored = false, minLogLevel = org.opalj.log.Error))
    // Setting up
    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = configTest1.projectJars,
      libcpFiles = configTest1.libraryJars
    )
    val spec = ArchitectureSpec(defaultRule = defaultRuleTest1, rules = rulesTest1)
    // Executing analysis
    val report = ArchitectureValidation.analyze(logger, project, spec, configTest1)

    // Checking if results are as expected
    // Total of 8 violations
    assert(report.violations.length == 8)
    // 1. Access of field in SomeClass from Main
    assert(report.violations.contains(Dependency(
      fromClass = "Main",
      toClass = "SomeClass",
      fromPackage = "org.example",
      toPackage = "org.example",
      fromJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      toJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      accessType = FIELD_ACCESS
    )))
    // 2. Main calls constructor of SomeClass
    assert(report.violations.contains(Dependency(
      fromClass = "Main",
      toClass = "SomeClass",
      fromPackage = "org.example",
      toPackage = "org.example",
      fromJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      toJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      accessType = METHOD_CALL
    )))
    // 3. Main has method where a parameter has type SomeClass
    assert(report.violations.contains(Dependency(
      fromClass = "Main",
      toClass = "SomeClass",
      fromPackage = "org.example",
      toPackage = "org.example",
      fromJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      toJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      accessType = PARAMETER_TYPE
    )))
    // 4. Main has method where the return type is SomeClass
    assert(report.violations.contains(Dependency(
      fromClass = "Main",
      toClass = "SomeClass",
      fromPackage = "org.example",
      toPackage = "org.example",
      fromJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      toJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      accessType = RETURN_TYPE
    )))
    // 5. Main has field with type SomeClass
    assert(report.violations.contains(Dependency(
      fromClass = "Main",
      toClass = "SomeClass",
      fromPackage = "org.example",
      toPackage = "org.example",
      fromJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      toJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      accessType = FIELD_TYPE
    )))
    // 6. Interface implementation of Interface by AbstractClass
    assert(report.violations.contains(Dependency(
      fromClass = "AbstractClass",
      toClass = "Interface",
      fromPackage = "org.example",
      toPackage = "org.example",
      fromJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      toJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      accessType = INTERFACE_IMPLEMENTATION
    )))
    // 7. InheritingClass inheriting from AbstractClass
    assert(report.violations.contains(Dependency(
      fromClass = "InheritingClass",
      toClass = "AbstractClass",
      fromPackage = "org.example",
      toPackage = "org.example",
      fromJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      toJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      accessType = INHERITANCE
    )))
    // 8. Method call of AbstractClass in InheritingClass
    // Note: This is quite a hidden dependency.
    // The implicit constructor of InheritingClass is calling the constructor of AbstractClass.
    assert(report.violations.contains(Dependency(
      fromClass = "InheritingClass",
      toClass = "AbstractClass",
      fromPackage = "org.example",
      toPackage = "org.example",
      fromJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      toJar = "AllDependenciesExample-1.0-SNAPSHOT.jar",
      accessType = METHOD_CALL
    )))
  }

  test("Analysis run with specification regarding Java Standard Library should give expected results") {
    OPALLogger.updateLogger(GlobalLogContext, new ConsoleOPALLogger(ansiColored = false, minLogLevel = org.opalj.log.Error))
    // Setting up
    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = configTest2.projectJars,
      libcpFiles = configTest2.libraryJars
    )
    val spec = ArchitectureSpec(defaultRule = defaultRuleTest2, rules = rulesTest2)
    // Executing analysis
    val report = ArchitectureValidation.analyze(logger, project, spec, configTest2)

    // Report will give warnings regarding that java.lang.System is not found in the project files.
    // However, the analysis should still be able to detect calls to it (e.g. System.out.println).
    // Ground truth only makes use of System.out.println in com.example.helper.Logger, all other classes use a Logger
    // to print.
    assert(report.violations.size == 1)
    // Why field access? --> System.out is a static PrintStream field of java.lang.System
    assert(report.violations.head == Dependency(
      fromClass = "Logger",
      toClass = "System",
      fromPackage = "com.example.helper",
      toPackage = "java.lang",
      fromJar = "ExampleHelper.jar",
      toJar = "[Unknown]",
      accessType = FIELD_ACCESS
    ))
  }
}
