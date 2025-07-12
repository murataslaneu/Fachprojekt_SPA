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
}
