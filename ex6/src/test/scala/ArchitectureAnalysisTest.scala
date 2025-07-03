import org.scalatest.funsuite.AnyFunSuite
import org.opalj.br.analyses.{Project, ProgressManagement}
import java.io.File
import java.net.URL

class ArchitectureAnalysisTest extends AnyFunSuite {

  /**
   * Runs the architecture analysis using the given JAR and config paths.
   * Adjust this if your analyze method or output type is different!
   */
  def runAnalysis(jarPath: String, configPath: String): String = {
    val project = Project(new File(jarPath))
    val parameters = Seq(configPath)
    val report = ArchitectureValidator.analyze(project, parameters, ProgressManagement.None)
    report.toString // If your report has violations/warnings as fields, use them here
  }

  // Covers allowed rules (only own jars allowed)
  test("Allowed rule: Only own JARs allowed") {
    val output = runAnalysis("tests/testproject.jar", "tests/ownJarsOnly/exampleConfig.json")
    // Output should NOT contain any violation keyword
    assert(!output.contains("violation"))
  }

  // Covers forbidden rules (banlist)
  test("Banlist: testproject -> pdfbox forbidden") {
    val output = runAnalysis("tests/testproject.jar", "tests/banlist/exampleConfig.json")
    // Output should contain pdfbox and violation
    assert(output.contains("pdfbox"))
    assert(output.contains("violation"))
  }

  // Covers all JARs forbidden
  test("All JARs forbidden: Any dependency triggers violation") {
    val output = runAnalysis("tests/testproject.jar", "tests/allJarsForbidden/exampleConfig.json")
    // There must be at least one violation
    assert(output.contains("violation"))
  }

  // Covers recursive exception (nested ALLOWED/FORBIDDEN paths)
  test("Recursive exception: internal forbidden, internal.special allowed") {
    val output = runAnalysis("tests/testproject.jar", "tests/simpleRecursive/exampleConfig.json")
    // internal.special should NOT be a violation, but internal should
    assert(!output.contains("testpkg.internal.special"))
    assert(output.contains("testpkg.internal"))
  }

  // Covers deep nested exception (multi-level override)
  test("Deep nested exception: legacy.temp forbidden, legacy allowed") {
    val output = runAnalysis("tests/testproject.jar", "tests/deepNestedException/exampleConfig.json")
    assert(output.contains("testpkg.internal.legacy.temp"))
    assert(!output.contains("testpkg.internal.legacy\""))
  }

  // Covers semantic warnings for missing packages/classes
  test("Semantic warning: missing package triggers warning") {
    val output = runAnalysis("tests/testproject.jar", "tests/packageExceptions/exampleConfig.json")
    assert(output.toLowerCase.contains("warning"))
    assert(output.contains("not found in project"))
  }

  // Covers error handling (invalid config or spec triggers exception)
  test("Invalid spec: missing required field triggers error") {
    assertThrows[Exception] {
      runAnalysis("tests/testproject.jar", "tests/main/architecture-spec-invalid.json")
    }
  }
}
