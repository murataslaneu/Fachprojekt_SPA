import com.typesafe.config.ConfigFactory
import data.{ArchitectureConfig, ArchitectureSpec, Dependency, Rule}
import helpers.{ArchitectureJsonIO, ArchitectureValidation}
import data.AccessType._
import org.opalj.log.GlobalLogContext
import org.scalatest.funsuite.AnyFunSuite

import java.io.File

class TestArchitectureValidation extends AnyFunSuite {

  test("Analysis run on example project with all possible dependencies should give expected results") {
    // Setting up
    val config = ArchitectureJsonIO.readConfig("src/test/allDependenciesExample/config.json")
    ArchitectureValidator.config = Some(config)
    val project = ArchitectureValidator.setupProject(List(), List(), completelyLoadLibraries = false, ConfigFactory.load)(GlobalLogContext)
    val spec = ArchitectureJsonIO.readSpecification(config.specificationFile)
    // Executing analysis
    val report = ArchitectureValidation.analyze(project, spec, config)

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
    // Configuration and setup
    val config = ArchitectureConfig(
      projectJars = List(
        //new File("ground_truth/ExampleMain.jar"),
        new File("ground_truth/ExampleHelper.jar"),
        //new File("ground_truth/ExampleDBAdapter.jar"),
      ),
      libraryJars = List(),
      specificationFile = "",
      outputJson = "",
      onlyMethodAndFieldAccesses = true
    )

    ArchitectureValidator.config = Some(config)
    val project = ArchitectureValidator.setupProject(List(), List(), completelyLoadLibraries = false, ConfigFactory.load)(GlobalLogContext)
    val spec = ArchitectureSpec(
      defaultRule = "ALLOWED",
      rules = List(
        Rule("ExampleMain.jar", "java.lang.System", "FORBIDDEN"),
        Rule("ExampleHelper.jar", "java.lang.System", "FORBIDDEN"),
        Rule("ExampleDBAdapter.jar", "java.lang.System", "FORBIDDEN")
      )
    )

    // Executing analysis
    val report = ArchitectureValidation.analyze(project, spec, config)
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
