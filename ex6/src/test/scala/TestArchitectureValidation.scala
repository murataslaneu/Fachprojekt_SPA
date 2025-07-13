import com.typesafe.config.ConfigFactory
import data.Dependency
import helpers.{ArchitectureJsonIO, ArchitectureValidation}
import data.AccessType._
import org.opalj.log.GlobalLogContext
import org.scalatest.funsuite.AnyFunSuite

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
}
