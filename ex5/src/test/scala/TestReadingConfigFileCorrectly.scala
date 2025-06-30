import helpers.JsonIO
import org.scalatest.funsuite.AnyFunSuite

class TestReadingConfigFileCorrectly extends AnyFunSuite {
  test("Loading config from json file gives expected results (all values manually set)") {
    val analysisConfig = JsonIO.readJsonConfig("src/test/testFiles/exampleConfig.json")
    // Check project jars
    assert(analysisConfig.projectJars.length == 2)
    assertResult("example/ExampleWithDeadCode.jar")(analysisConfig.projectJars.head.getPath.replace('\\', '/'))
    assertResult("example/MinimalExample.jar")(analysisConfig.projectJars(1).getPath.replace('\\', '/'))
    // Check library jars
    assert(analysisConfig.libraryJars.isEmpty)

    // Check interactive
    assertResult(true)(analysisConfig.interactive)

    // Check outputJson
    assertResult("resultFile.json")(analysisConfig.outputJson)
  }

  test("Loading config from json file gives expected results (minimal number of options set)") {
    val analysisConfig = JsonIO.readJsonConfig("src/test/testFiles/exampleConfig2.json")

      // Check project jars
      assert(analysisConfig.projectJars.length == 1)
      assertResult("example/ExampleWithDeadCode.jar")(analysisConfig.projectJars.head.getPath.replace('\\', '/'))
      // Check library jars
      assert(analysisConfig.libraryJars.isEmpty)

      // Check interactive
      assertResult(true)(analysisConfig.interactive)

      // Check outputJson
      assertResult("result.json")(analysisConfig.outputJson)
  }

  test("Adds .json to the end of outputJson if missing") {
    val analysisConfig = JsonIO.readJsonConfig("src/test/testFiles/exampleConfig3.json")
    assert(analysisConfig.outputJson == "resultFile.json")
  }

  test("Invalid input throws exception") {
    assertThrows[java.io.IOException](JsonIO.readJsonConfig("src/test/testFiles/invalidConfig.json"))
    assertThrows[java.io.IOException](JsonIO.readJsonConfig("src/test/testFiles/invalidConfig2.json"))
    assertThrows[NoSuchElementException](JsonIO.readJsonConfig("src/test/testFiles/invalidConfig3.json"))
  }
}
