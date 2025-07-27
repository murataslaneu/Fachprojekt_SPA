package analyses.F_ArchitectureValidator.helpers

import analyses.F_ArchitectureValidator.data.AccessType._
import analyses.F_ArchitectureValidator.data._
import configs.{ArchitectureValidatorConfig, StaticAnalysisConfig}
import main.Main
import org.opalj.br.analyses.Project
import org.opalj.br.instructions._
import org.opalj.br.{ClassFile, ObjectType, ReferenceType}
import org.slf4j.Logger

import java.io.File
import java.net.URL
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object ArchitectureValidation {

  /**
   * Extracts JAR name from a class file path
   */
  private def getJarName(classFile: ClassFile, project: Project[URL]): String = {
    project.source(classFile) match {
      case Some(source) =>
        val sourcePath = source.toString
        if (sourcePath.contains("jar:file:")) {
          val jarPath = sourcePath.substring(sourcePath.indexOf("jar:file:") + 9)
          val jarName = jarPath.substring(0, jarPath.lastIndexOf("!"))
          new File(jarName).getName
        } else {
          "[Unknown]"
        }
      case None => "[Unknown]"
    }
  }

  /**
   * Helper method to get package name from ReferenceType
   */
  private def getPackageName(refType: ReferenceType): String = {
    refType match {
      case objType: ObjectType => objType.packageName.replace("/", ".")
      case _ => ""
    }
  }

  /**
   * Helper method to convert ReferenceType to Java class name
   */
  private def getJavaClassName(refType: ReferenceType): String = {
    refType match {
      case objType: ObjectType => objType.simpleName
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
          case None => "[Unknown]"
        }
      case _ => "[Unknown]"
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
        (dependency.fromClassFqn, dependency.toClassFqn), // Class to Class
        (dependency.fromClassFqn, dependency.toPackage), // Class to Package
        (dependency.fromClassFqn, dependency.toJar), // Class to JAR
        (dependency.fromPackage, dependency.toClassFqn), // Package to Class
        (dependency.fromPackage, dependency.toPackage), // Package to Package
        (dependency.fromPackage, dependency.toJar), // Package to JAR
        (dependency.fromJar, dependency.toClassFqn), // JAR to Class
        (dependency.fromJar, dependency.toPackage), // JAR to Package
        (dependency.fromJar, dependency.toJar) // JAR to JAR
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
  private def validateSpecification(spec: ArchitectureSpec, project: Project[URL]): (Int, RecursiveWarnings) = {
    var warningsCount = 0

    // Get all available entities from the project
    val allClasses = project.allClassFiles.map(_.thisType.toJava).toSet
    val allPackages = project.allClassFiles.map(_.thisType.packageName.replace('/', '.')).toSet
    val allJars = project.allClassFiles.map(cf => getJarName(cf, project)).toSet

    /** Removes [[RecursiveWarnings]] objects that contain no warnings. */
    def cleanWarnings(rw: RecursiveWarnings): Option[RecursiveWarnings] = {
      val cleanedInner = rw.innerWarnings.flatMap {
        case (k, v) => cleanWarnings(v).map(k -> _)
      }

      if (rw.warnings.nonEmpty || cleanedInner.nonEmpty)
        Some(RecursiveWarnings(rw.warnings, cleanedInner))
      else
        None
    }

    /**
     * Checks whether an entity specified in a rule can be found in the project.
     *
     * @param entity The entity to check
     * @return Possibly a String containing a warning (ideally None)
     */
    def validateEntity(entity: String): Option[String] = {
      if (entity.endsWith(".jar")) {
        if (!allJars.contains(entity)) {
          return Some(s"JAR '$entity' not found in project")
        }
      } else if (entity.contains(".") && entity.split("\\.").last.matches("^[A-Z].*")) {
        if (!allClasses.contains(entity)) {
          return Some(s"Class '$entity' not found in project")
        }
      } else {
        if (!allPackages.contains(entity)) {
          return Some(s"Package '$entity' not found in project")
        }
      }
      None
    }

    /**
     * Checks whether a rule is sensical in the context of the project or is (maybe) not needed or incorrect.
     *
     * @param rule The [[Rule]] to check.
     * @param parentRule The [[Rule]] that contained rule.
     * @return [[RecursiveWarnings]] regarding this rule and its children.
     */
    def validateRule(rule: Rule, parentRule: Option[Rule] = None): RecursiveWarnings = {
      val currentWarnings = ListBuffer.empty[String]

      // Validate entities exist
      val entityFromWarning = validateEntity(rule.from)
      if (entityFromWarning.isDefined) {
        currentWarnings += entityFromWarning.get
        warningsCount += 1
      }
      val entityToWarning = validateEntity(rule.to)
      if (entityToWarning.isDefined) {
        currentWarnings += entityToWarning.get
        warningsCount += 1
      }

      // Semantic validation: check if exception makes sense with parent rule
      parentRule match {
        case Some(parent) =>
          // Exception should be more specific than parent rule
          if (rule.`type` == parent.`type`) {
            currentWarnings += "Exception rule has same type as parent rule"
            warningsCount += 1
          }

          // Exception should be within the scope of parent rule
          if (!isEntitySubsetOf(rule.from, parent.from) || !isEntitySubsetOf(rule.to, parent.to)) {
            currentWarnings += "Exception rule may not be within parent rule scope"
            warningsCount += 1
          }
        case None => if (rule.`type` == spec.defaultRule) {
          currentWarnings += "Base rule has same type as default rule"
          warningsCount += 1
        }
      }

      // Validate exceptions recursively
      val innerWarnings = rule.except.getOrElse(Nil).map { child =>
        val key = s"${child.from} -> ${child.to}"
        key -> validateRule(child, Some(rule))
      }.toMap
      RecursiveWarnings(currentWarnings.toList, innerWarnings)
    }

    var defaultRuleWarning: Option[String] = None
    // Validate default rule
    if (spec.defaultRule != "ALLOWED" && spec.defaultRule != "FORBIDDEN") {
      defaultRuleWarning = Some(s"Invalid defaultRule '${spec.defaultRule}'. Should be 'ALLOWED' or 'FORBIDDEN'")
    }

    // Validate all rules
    val warnings = spec.rules.map { rule: Rule =>
        val ruleWarnings = cleanWarnings(validateRule(rule))
        if (ruleWarnings.isDefined) {
          Some(s"${rule.from} -> ${rule.to}" -> ruleWarnings.get)
        }
        else None
      }.filter(element => element.isDefined)
      .map(element => element.get)
      .toMap

    (warningsCount, RecursiveWarnings(
      if (defaultRuleWarning.isDefined) List(defaultRuleWarning.get) else Nil,
      warnings
    ))
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
  private def findAllDependencies(project: Project[URL], config: ArchitectureValidatorConfig): Set[Dependency] = {
    val dependencies = mutable.Set.empty[Dependency]

    /**
     * Adds dependency to the dependencies set.
     *
     * Dependencies to the exact same class are ignored as they are not interesting.
     */
    def addDependency(fromClass: String, fromPackage: String, fromJar: String, refType: ReferenceType, accessType: AccessType): Unit = {
      val targetClass = getJavaClassName(refType)
      val targetPackage = getPackageName(refType)
      val targetJar = getJarNameFromRefType(refType, project)

      // Only add dependency if it isn't the exact same class
      val isSameClass = fromClass == targetClass && fromPackage == targetPackage && fromJar == targetJar
      // Add dependency depending on whether all access types are relevant or only method and field accesses
      val relevantAccessType = !config.onlyMethodAndFieldAccesses || accessType == METHOD_CALL || accessType == FIELD_ACCESS
      if (!isSameClass && relevantAccessType) {
        dependencies += Dependency(fromClass, targetClass, fromPackage, targetPackage, fromJar, targetJar, accessType)
      }
    }

    project.allProjectClassFiles.foreach { classFile =>
      val fromClass = classFile.thisType.simpleName
      val fromPackage = classFile.thisType.packageName.replace("/", ".")
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

  //  /**
  //   * Debug method to show all packages and classes in the project
  //   */
  //  private def debugPackageNames(logger: Logger, project: Project[URL]): Unit = {
  //    logger.debug("=== DEBUG: All packages in project ===")
  //    val allPackages = project.allClassFiles.map(_.thisType.packageName).toSet.toList.sorted
  //    allPackages.foreach(pkg => logger.debug(s"  Package: $pkg"))
  //
  //    logger.debug(s"\nTotal packages: ${allPackages.size}")
  //    logger.debug(s"Total classes: ${project.allClassFiles.size}")
  //
  //    // Find util packages specifically
  //    val utilClasses = project.allClassFiles.filter(_.thisType.packageName.contains("util"))
  //    logger.debug(s"\nClasses containing 'util': ${utilClasses.size}")
  //    utilClasses.take(10).foreach(cls =>
  //      logger.debug(s"  ${cls.thisType.toJava} (package: ${cls.thisType.packageName})")
  //    )
  //
  //    logger.debug("=" * 50)
  //  }

  /**
   * Main analysis method - Enhanced version
   */
  def analyze(logger: Logger, project: Project[URL], specification: ArchitectureSpec, config: StaticAnalysisConfig): ArchitectureReport = {
    val startTime = System.currentTimeMillis()

    // DEBUG: Show all packages and classes
    //debugPackageNames(project)

    // Validate specification
    logger.info("Validating specification...")
    val (warningsCount, warnings) = validateSpecification(specification, project)
    if (warningsCount > 0) logger.warn(s"Generated $warningsCount warnings regarding the specification.")
    else logger.info("Specification valid, generated no warnings.")

    // Find all dependencies
    logger.info("Finding all dependencies...")
    val allDependencies = findAllDependencies(project, config.architectureValidator)
    logger.info(s"Found ${allDependencies.size} total dependencies.")
    logger.info(s"Checking for each dependency whether it is allowed or not...")

    val violations = mutable.ListBuffer.empty[Dependency]

    // Check each dependency against the specification
    allDependencies.foreach { dependency =>
      if (!isAllowed(specification, dependency)) {
        violations += dependency
      }
    }

    val endTime = System.currentTimeMillis()
    val runtime = endTime - startTime
    val duration = f"${runtime / 1000.0}%.3f".replace(',', '.')

    logger.info(s"Dependency finding and checking completed in $duration seconds.")

    ArchitectureReport(
      config.projectJars.map(_.getPath.replace('\\', '/')).toList,
      Main.inputJsonPath,
      java.time.LocalDateTime.now(),
      runtime,
      config.architectureValidator.onlyMethodAndFieldAccesses,
      violations.toList,
      warningsCount,
      warnings
    )
  }
}