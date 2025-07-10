package helpers

import data.AccessType._
import data._
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.{GETFIELD, GETSTATIC, INVOKEINTERFACE, INVOKESPECIAL, INVOKESTATIC, INVOKEVIRTUAL, PUTFIELD, PUTSTATIC}
import org.opalj.br.{ClassFile, ObjectType, ReferenceType}
import play.api.libs.json.Json

import java.io.File
import java.net.URL
import scala.collection.mutable
import scala.io.Source

object ArchitectureValidation {

  /**
   * Reads architecture specification from JSON file
   */
  private def readSpecification(specFile: String): ArchitectureSpec = {
    val source = Source.fromFile(specFile)
    val json = try Json.parse(source.mkString) finally source.close()
    json.as[ArchitectureSpec]
  }

  /**
   * Extracts JAR name from a class file path
   */
  private def getJarName(classFile: ClassFile, project: Project[URL]): String = {
    project.source(classFile) match {
      case Some(source) =>
        val sourcePath = source.toString
        if (sourcePath.contains("jar:file:")) {
          val jarPath = sourcePath.substring(sourcePath.indexOf("jar:file:") + 9)
          val jarName = jarPath.substring(0, jarPath.indexOf("!"))
          new File(jarName).getName
        } else {
          "Unknown.jar"
        }
      case None => "Unknown.jar"
    }
  }

  def matchesPackage(rulePackage: String, actualPackage: String): Boolean = {
    actualPackage == rulePackage || actualPackage.startsWith(rulePackage + ".")
  }

  /**
   * Helper method to get package name from ReferenceType
   */
  private def getPackageName(refType: ReferenceType): String = {
    refType match {
      case objType: ObjectType => objType.packageName
      case _ => ""
    }
  }

  /**
   * Helper method to convert ReferenceType to Java class name
   */
  private def getJavaClassName(refType: ReferenceType): String = {
    refType match {
      case objType: ObjectType => objType.toJava
      case _ => refType.toJava
    }
  }

  /**
   * Helper method to get JAR name from ReferenceType
   */
  private def getJarNameFromRefType(refType: ReferenceType, project: Project[URL]): String = {
    refType match {
      case objType: ObjectType =>
        project.classFile(objType) match {
          case Some(classFile) => getJarName(classFile, project)
          case None => "Unknown.jar"
        }
      case _ => "Unknown.jar"
    }
  }

  /**
   * Improved rule matching with better semantics
   */
  private def matchesRule(rule: Rule, fromEntity: String, toEntity: String): Boolean = {
    def matchesEntity(ruleEntity: String, actualEntity: String): Boolean = {
      if (ruleEntity.endsWith(".jar")) {
        // JAR matching - exact match
        actualEntity == ruleEntity
      } else if (ruleEntity.contains(".") && ruleEntity.split("\\.").last.matches("^[A-Z].*")) {
        // Class name matching (starts with uppercase)
        actualEntity == ruleEntity || actualEntity.endsWith("." + ruleEntity)
      } else {
        // Package name matching
        actualEntity == ruleEntity || actualEntity.startsWith(ruleEntity + ".")
      }
    }

    matchesEntity(rule.from, fromEntity) && matchesEntity(rule.to, toEntity)
  }

  /**
   * Evaluates if a dependency is allowed based on the specification
   * Improved logic for handling all combinations
   */
  private def isAllowed(spec: ArchitectureSpec, dependency: Dependency): Boolean = {

    def evaluateRule(rule: Rule): Option[Boolean] = {
      // Check all possible matching combinations
      val matchingCombinations = List(
        (dependency.fromClass, dependency.toClass),     // Class to Class
        (dependency.fromClass, dependency.toPackage),   // Class to Package
        (dependency.fromClass, dependency.toJar),       // Class to JAR
        (dependency.fromPackage, dependency.toClass),   // Package to Class
        (dependency.fromPackage, dependency.toPackage), // Package to Package
        (dependency.fromPackage, dependency.toJar),     // Package to JAR
        (dependency.fromJar, dependency.toClass),       // JAR to Class
        (dependency.fromJar, dependency.toPackage),     // JAR to Package
        (dependency.fromJar, dependency.toJar)          // JAR to JAR
      )

      // Check if any combination matches the rule
      val matches = matchingCombinations.exists { case (from, to) =>
        matchesRule(rule, from, to)
      }

      if (matches) {
        val baseResult = rule.`type` == "ALLOWED"

        // Check exceptions recursively
        rule.except match {
          case Some(exceptions) =>
            exceptions.foreach { exception =>
              evaluateRule(exception) match {
                case Some(exceptionResult) => return Some(exceptionResult)
                case None => // Continue checking other exceptions
              }
            }
            Some(baseResult)
          case None => Some(baseResult)
        }
      } else {
        None
      }
    }

    // Evaluate all rules in order
    spec.rules.foreach { rule =>
      evaluateRule(rule) match {
        case Some(result) => return result
        case None => // Continue to next rule
      }
    }

    // No rule matched, return default
    spec.defaultRule == "ALLOWED"
  }

  /**
   * Enhanced specification validation with semantic error checking
   */
  private def validateSpecification(spec: ArchitectureSpec, project: Project[URL]): List[String] = {
    val warnings = mutable.ListBuffer.empty[String]

    // Get all available entities from the project
    val allClasses = project.allClassFiles.map(_.thisType.toJava).toSet
    val allPackages = project.allClassFiles.map(_.thisType.packageName).toSet
    val allJars = project.allClassFiles.map(cf => getJarName(cf, project)).toSet

    def validateEntity(entity: String, entityType: String): Unit = {
      if (entity.endsWith(".jar")) {
        if (!allJars.contains(entity)) {
          warnings += s"Warning: JAR '$entity' not found in project"
        }
      } else if (entity.contains(".") && entity.split("\\.").last.matches("^[A-Z].*")) {
        if (!allClasses.contains(entity)) {
          warnings += s"Warning: Class '$entity' not found in project"
        }
      } else {
        if (!allPackages.contains(entity)) {
          warnings += s"Warning: Package '$entity' not found in project"
        }
      }
    }

    def validateRule(rule: Rule, parentRule: Option[Rule] = None, depth: Int = 0): Unit = {
      val indent = "  " * depth

      // Validate entities exist
      validateEntity(rule.from, "from")
      validateEntity(rule.to, "to")

      // Semantic validation: check if exception makes sense with parent rule
      parentRule match {
        case Some(parent) =>
          // Exception should be more specific than parent rule
          if (rule.`type` == parent.`type`) {
            warnings += s"${indent}Warning: Exception rule has same type as parent rule"
          }

          // Exception should be within the scope of parent rule
          if (!isEntitySubsetOf(rule.from, parent.from) || !isEntitySubsetOf(rule.to, parent.to)) {
            warnings += s"${indent}Warning: Exception rule '${rule.from}' -> '${rule.to}' may not be within parent rule scope"
          }
        case None => // Top-level rule, no parent validation needed
      }

      // Validate exceptions recursively
      rule.except.foreach { exceptions =>
        exceptions.foreach(validateRule(_, Some(rule), depth + 1))
      }
    }

    // Validate default rule
    if (spec.defaultRule != "ALLOWED" && spec.defaultRule != "FORBIDDEN") {
      warnings += s"Warning: Invalid defaultRule '${spec.defaultRule}'. Should be 'ALLOWED' or 'FORBIDDEN'"
    }

    // Validate all rules
    spec.rules.foreach(validateRule(_))

    warnings.toList
  }

  /**
   * Check if entity1 is a subset of entity2 (for semantic validation)
   */
  private def isEntitySubsetOf(entity1: String, entity2: String): Boolean = {
    if (entity1 == entity2) return true

    // If entity2 is a package and entity1 is a subpackage or class in that package
    if (!entity2.endsWith(".jar") && !entity2.contains(".") || entity2.split("\\.").last.matches("^[a-z].*")) {
      entity1.startsWith(entity2 + ".")
    } else {
      false
    }
  }

  /**
   * Enhanced dependency detection including potential calls
   */
  private def findAllDependencies(project: Project[URL]): Set[Dependency] = {
    val dependencies = mutable.Set.empty[Dependency]

    def addDependency(fromClass: String, fromPackage: String, fromJar: String, refType: ReferenceType, accessType: AccessType): Unit = {
      val targetClass = getJavaClassName(refType)
      val targetPackage = getPackageName(refType)
      val targetJar = getJarNameFromRefType(refType, project)
      dependencies += Dependency(fromClass, targetClass, fromPackage, targetPackage, fromJar, targetJar, accessType)
    }

    project.allClassFiles.foreach { classFile =>
      val fromClass = classFile.thisType.toJava
      val fromPackage = classFile.thisType.packageName
      val fromJar = getJarName(classFile, project)

      // Check superclass dependencies
      classFile.superclassType.foreach { superType =>
        addDependency(fromClass, fromPackage, fromJar, superType, INHERITANCE)
      }

      // Check interface dependencies
      classFile.interfaceTypes.foreach { interfaceType =>
        addDependency(fromClass, fromPackage, fromJar, interfaceType, INTERFACE_IMPLEMENTATION)
      }

      // Check field type dependencies
      classFile.fields.foreach { field =>
        field.fieldType match {
          case refType: ReferenceType => addDependency(fromClass, fromPackage, fromJar, refType, FIELD_TYPE)
          case _ => // Ignore primitive types
        }
      }

      // Check method dependencies
      classFile.methods.foreach { method =>
        // Return type dependencies
        method.returnType match {
          case refType: ReferenceType => addDependency(fromClass, fromPackage, fromJar, refType, RETURN_TYPE)
          case _ => // Ignore primitive types
        }

        // Parameter type dependencies
        method.parameterTypes.foreach {
          case refType: ReferenceType => addDependency(fromClass, fromPackage, fromJar, refType, PARAMETER_TYPE)
          case _ => // Ignore primitive types
        }

        // Method body dependencies (existing logic)
        if (method.body.isDefined) {
          val methodBody = method.body.get

          methodBody.foreach { pcInstruction =>
            val instruction = pcInstruction.instruction

            instruction match {
              case invoke: INVOKEVIRTUAL => addDependency(fromClass, fromPackage, fromJar, invoke.declaringClass, METHOD_CALL)
              case invoke: INVOKESPECIAL => addDependency(fromClass, fromPackage, fromJar, invoke.declaringClass, METHOD_CALL)
              case invoke: INVOKESTATIC => addDependency(fromClass, fromPackage, fromJar, invoke.declaringClass, METHOD_CALL)
              case invoke: INVOKEINTERFACE => addDependency(fromClass, fromPackage, fromJar, invoke.declaringClass, METHOD_CALL)
              case field: GETFIELD => addDependency(fromClass, fromPackage, fromJar, field.declaringClass, FIELD_ACCESS)
              case field: GETSTATIC => addDependency(fromClass, fromPackage, fromJar, field.declaringClass, FIELD_ACCESS)
              case field: PUTFIELD => addDependency(fromClass, fromPackage, fromJar, field.declaringClass, FIELD_ACCESS)
              case field: PUTSTATIC => addDependency(fromClass, fromPackage, fromJar, field.declaringClass, FIELD_ACCESS)
              case _ => // Ignore other instructions
            }
          }
        }
      }
    }

    dependencies.toSet
  }

  /**
   * Debug method to show all packages and classes in the project
   */
  private def debugPackageNames(project: Project[URL]): Unit = {
    println("=== DEBUG: All packages in project ===")
    val allPackages = project.allClassFiles.map(_.thisType.packageName).toSet.toList.sorted
    allPackages.foreach(pkg => println(s"  Package: $pkg"))

    println(s"\nTotal packages: ${allPackages.size}")
    println(s"Total classes: ${project.allClassFiles.size}")

    // Find util packages specifically
    val utilClasses = project.allClassFiles.filter(_.thisType.packageName.contains("util"))
    println(s"\nClasses containing 'util': ${utilClasses.size}")
    utilClasses.take(10).foreach(cls =>
      println(s"  ${cls.thisType.toJava} (package: ${cls.thisType.packageName})")
    )

    println("=" * 50)
  }

  /**
   * Main analysis method - Enhanced version
   */
  def analyze(project: Project[URL], specFile: String, config: ArchitectureConfig): ArchitectureReport = {
    val startTime = System.currentTimeMillis()

    // Read specification
    val spec = readSpecification(specFile)

    // DEBUG: Show all packages and classes
    debugPackageNames(project)

    // Validate specification
    val warnings = validateSpecification(spec, project)

    // Find all dependencies
    println("Finding all dependencies...")
    val allDependencies = findAllDependencies(project)
    println(s"Found ${allDependencies.size} total dependencies")

    val violations = mutable.ListBuffer.empty[Dependency]

    // Check each dependency against the specification
    allDependencies.foreach { dependency =>
      if (!isAllowed(spec, dependency)) {
        violations += dependency
      }
    }

    val endTime = System.currentTimeMillis()
    val runtime = endTime - startTime

    println(s"Analysis completed in ${runtime}ms")
    println(s"Total dependencies analyzed: ${allDependencies.size}")
    println(s"Violations found: ${violations.size}")

    ArchitectureReport(
      config.projectJars.map(_.getPath.replace('\\', '/')),
      specFile,
      java.time.LocalDateTime.now(),
      runtime,
      violations.toList,
      warnings
    )
  }
}