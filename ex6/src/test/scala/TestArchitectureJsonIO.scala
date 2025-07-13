import data.{ArchitectureConfig, ArchitectureSpec, Rule}
import helpers.ArchitectureJsonIO
import org.scalatest.funsuite.AnyFunSuite

import java.io.File

/**
 * Tests that check the functionality of the JsonIO helper for the analysis.
 */
class TestArchitectureJsonIO extends AnyFunSuite {
  private val expectedConfig = ArchitectureConfig(
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

  private val expectedSpec = ArchitectureSpec(
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

  test("JsonIO reads config file correctly") {
    val actualConfig = ArchitectureJsonIO.readConfig("ground_truth/config.json")
    assert(expectedConfig == actualConfig)
  }

  test("JsonIO reads architecture specification correctly") {
    val actualSpec = ArchitectureJsonIO.readSpecification("ground_truth/spec.json")
    assert(expectedSpec == actualSpec)
  }

  test("JsonIO rejects invalid configs") {
    assertThrows[java.io.IOException](ArchitectureJsonIO.readConfig("src/test/invalidConfigsAndSpecs/invalid_project_file.json"))
    assertThrows[java.io.IOException](ArchitectureJsonIO.readConfig("src/test/invalidConfigsAndSpecs/invalid_library_file.json"))
    assertThrows[java.io.IOException](ArchitectureJsonIO.readConfig("src/test/invalidConfigsAndSpecs/invalid_spec_file.json"))
    assertThrows[IllegalArgumentException](ArchitectureJsonIO.readConfig("src/test/invalidConfigsAndSpecs/invalid_spec_file2.json"))
    val config = ArchitectureJsonIO.readConfig("src/test/invalidConfigsAndSpecs/config_should_autoadd.json")
    assert(config.outputJson == "ground_truth/architecture-report.txt.json")
    // Final test: Valid config should be read properly
    ArchitectureJsonIO.readConfig("src/test/invalidConfigsAndSpecs/valid_config.json")
  }
}
