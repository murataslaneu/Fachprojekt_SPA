import analyses.F_ArchitectureValidator.data.Rule
import configs._
import data.{IgnoredCall, SelectedMethodsOfClass}
import org.scalatest.funsuite.AnyFunSuite
import util.JsonIO

import java.io.File
import java.nio.file.{Files, Path}

/**
 * Tests checking whether reading the config leads to the expected results
 */
class TestJsonIO extends AnyFunSuite {
  val jsonIO = new JsonIO()

  test("Default config can be created and read from correctly") {
    // Creating default json file in the test folder
    val jsonPathString = "src/test/defaultConfig.json"
    Files.deleteIfExists(Path.of(jsonPathString))
    jsonIO.writeDefaultJson(jsonPathString)

    // Reading config: Initialization step
    val (outputPath, jsonConfig) = jsonIO.readAnalysisConfigInit(jsonPathString)
    Files.deleteIfExists(Path.of(jsonPathString))
    assertResult("analysis")(outputPath)

    // Reading config: Actual reading, checking core options
    val config = jsonIO.readStaticAnalysisConfig(jsonConfig, outputPath)
    assertResult(Array.empty[File])(config.projectJars)
    assertResult(Array.empty[File])(config.libraryJars)
    assertResult("analysis")(config.resultsOutputPath)

    // Checking each sub-analysis config

    val godClassDetector = config.godClassDetector
    assertResult(false)(godClassDetector.execute)
    assertResult(GodClassDetectorConfig.DEFAULT_WMC_THRESH)(godClassDetector.wmcThresh)
    assertResult(GodClassDetectorConfig.DEFAULT_TCC_THRESH)(godClassDetector.tccThresh)
    assertResult(GodClassDetectorConfig.DEFAULT_ATFD_THRESH)(godClassDetector.atfdThresh)
    assertResult(GodClassDetectorConfig.DEFAULT_NOF_THRESH)(godClassDetector.nofThresh)

    val criticalMethodsDetector = config.criticalMethodsDetector
    assertResult(false)(criticalMethodsDetector.execute)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_CRITICAL_METHODS)(criticalMethodsDetector.criticalMethods)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_IGNORE)(criticalMethodsDetector.ignore)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_CALL_GRAPH_ALGORITHM_NAME)(criticalMethodsDetector.callGraphAlgorithmName)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_ENTRY_POINTS_FINDER)(criticalMethodsDetector.entryPointsFinder)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_CUSTOM_ENTRY_POINTS)(criticalMethodsDetector.customEntryPoints)

    val tplUsageAnalyzer = config.tplUsageAnalyzer
    assertResult(false)(tplUsageAnalyzer.execute)
    assertResult(TPLUsageAnalyzerConfig.DEFAULT_COUNT_ALL_METHODS)(tplUsageAnalyzer.countAllMethods)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_CALL_GRAPH_ALGORITHM_NAME)(tplUsageAnalyzer.callGraphAlgorithmName)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_ENTRY_POINTS_FINDER)(tplUsageAnalyzer.entryPointsFinder)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_CUSTOM_ENTRY_POINTS)(tplUsageAnalyzer.customEntryPoints)

    val criticalMethodsRemover = config.criticalMethodsRemover
    assertResult(false)(criticalMethodsRemover.execute)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_CRITICAL_METHODS)(criticalMethodsRemover.criticalMethods)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_IGNORE)(criticalMethodsRemover.ignore)

    val tplMethodsRemover = config.tplMethodsRemover
    assertResult(false)(tplMethodsRemover.execute)
    assertResult("DEFAULT")(tplMethodsRemover.tplJar)
    assertResult(TPLMethodsRemoverConfig.DEFAULT_INCLUDE_PUBLIC_METHODS)(tplMethodsRemover.includeNonPublicMethods)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_CALL_GRAPH_ALGORITHM_NAME)(tplMethodsRemover.callGraphAlgorithmName)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_ENTRY_POINTS_FINDER)(tplMethodsRemover.entryPointsFinder)
    assertResult(CriticalMethodsDetectorConfig.DEFAULT_CUSTOM_ENTRY_POINTS)(tplMethodsRemover.customEntryPoints)

    val deadCodeDetector = config.deadCodeDetector
    assertResult(false)(deadCodeDetector.execute)
    assertResult(DeadCodeDetectorConfig.DEFAULT_COMPLETELY_LOAD_LIBRARIES)(deadCodeDetector.completelyLoadLibraries)
    assertResult(DeadCodeDetectorConfig.DEFAULT_DOMAINS)(deadCodeDetector.domains)

    val architectureValidator = config.architectureValidator
    assertResult(false)(architectureValidator.execute)
    assertResult(ArchitectureValidatorConfig.DEFAULT_ONLY_METHOD_AND_FIELD_ACCESSES)(architectureValidator.onlyMethodAndFieldAccesses)
    assertResult(ArchitectureValidatorConfig.DEFAULT_DEFAULT_RULE)(architectureValidator.defaultRule)
    assertResult(ArchitectureValidatorConfig.DEFAULT_RULES)(architectureValidator.rules)
  }

  test("Reading all custom config can be read as expected") {
    // Creating default json file in the test folder
    val jsonPathString = "src/test/allCustomConfig.json"

    // Reading config: Initialization step
    val (outputPath, jsonConfig) = jsonIO.readAnalysisConfigInit(jsonPathString)
    assertResult("src/test/analysis")(outputPath)

    // Reading config: Actual reading, checking core options
    val config = jsonIO.readStaticAnalysisConfig(jsonConfig, outputPath)
    assertResult(Array(
      new File("example/commons-lang3-3.18.1.jar")
    ))(config.projectJars)
    assertResult(Array(
      new File("example/lib/apiguardian-api-1.1.2.jar"),
      new File("example/lib/asm-9.8.jar"),
      new File("example/lib/commons-lang3-3.17.0.jar"),
      new File("example/lib/commons-math3-3.6.1.jar"),
      new File("example/lib/commons-text-1.13.1.jar"),
      new File("example/lib/easymock-5.6.0.jar"),
      new File("example/lib/jmh-core-1.37.jar"),
      new File("example/lib/jmh-generator-annprocess-1.37.jar"),
      new File("example/lib/jopt-simple-5.0.4.jar"),
      new File("example/lib/jsr305-3.0.2.jar"),
      new File("example/lib/objenesis-3.4.jar"),
      new File("example/lib/opentest4j-1.3.0.jar")
    ))(config.libraryJars)
    assertResult("src/test/analysis")(config.resultsOutputPath)

    // Checking each sub-analysis config
    val godClassDetector = config.godClassDetector
    assertResult(true)(godClassDetector.execute)
    assertResult(50)(godClassDetector.wmcThresh)
    assertResult(0.5)(godClassDetector.tccThresh)
    assertResult(20)(godClassDetector.atfdThresh)
    assertResult(20)(godClassDetector.nofThresh)

    val criticalMethodsDetector = config.criticalMethodsDetector
    assertResult(true)(criticalMethodsDetector.execute)
    assertResult(List(SelectedMethodsOfClass("java.lang.System", List("arraycopy"))))(criticalMethodsDetector.criticalMethods)
    assertResult(Set(IgnoredCall("org.openjdk.jmh.util.ClassUtils", "denseClassNames", "java.lang.System", "arraycopy")))(criticalMethodsDetector.ignore)
    assertResult("xta")(criticalMethodsDetector.callGraphAlgorithmName)
    assertResult("applicationwithjre")(criticalMethodsDetector.entryPointsFinder)
    assertResult(List(SelectedMethodsOfClass("org.apache.commons.lang3.ArrayUtils", List("add"))))(criticalMethodsDetector.customEntryPoints)

    val tplUsageAnalyzer = config.tplUsageAnalyzer
    assertResult(true)(tplUsageAnalyzer.execute)
    assertResult(true)(tplUsageAnalyzer.countAllMethods)
    assertResult("cha")(tplUsageAnalyzer.callGraphAlgorithmName)
    assertResult("library")(tplUsageAnalyzer.entryPointsFinder)
    assertResult(List.empty[SelectedMethodsOfClass])(tplUsageAnalyzer.customEntryPoints)

    val criticalMethodsRemover = config.criticalMethodsRemover
    assertResult(true)(criticalMethodsRemover.execute)
    assertResult(List(SelectedMethodsOfClass("java.lang.System", List("getSecurityManager", "setSecurityManager"))))(criticalMethodsRemover.criticalMethods)
    assertResult(Set(IgnoredCall("org.openjdk.jmh.util.ClassUtils", "denseClassNames", "java.lang.System", "getSecurityManager")))(criticalMethodsRemover.ignore)

    val tplMethodsRemover = config.tplMethodsRemover
    assertResult(true)(tplMethodsRemover.execute)
    assertResult("example/lib/commons-math3-3.6.1.jar")(tplMethodsRemover.tplJar)
    assertResult(false)(tplMethodsRemover.includeNonPublicMethods)
    assertResult("rta")(tplMethodsRemover.callGraphAlgorithmName)
    assertResult("custom")(tplMethodsRemover.entryPointsFinder)
    assertResult(List.empty[SelectedMethodsOfClass])(tplMethodsRemover.customEntryPoints)

    val deadCodeDetector = config.deadCodeDetector
    assertResult(true)(deadCodeDetector.execute)
    assertResult(false)(deadCodeDetector.completelyLoadLibraries)
    assertResult(List[Int](1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13))(deadCodeDetector.domains)

    val architectureValidator = config.architectureValidator
    assertResult(true)(architectureValidator.execute)
    assertResult(true)(architectureValidator.onlyMethodAndFieldAccesses)
    assertResult("FORBIDDEN")(architectureValidator.defaultRule)
    assertResult(List(Rule("commons-lang3-3.18.1.jar", "commons-lang3-3.18.1.jar", "ALLOWED", Some(List(Rule("commons-lang3-3.18.1.jar", "commons-lang3-3.18.1.jar", "FORBIDDEN", None))))))(architectureValidator.rules)
  }
}
