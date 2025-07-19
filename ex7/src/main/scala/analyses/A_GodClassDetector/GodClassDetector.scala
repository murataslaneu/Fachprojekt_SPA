package analyses.A_GodClassDetector

import analyses.SubAnalysis
import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig
import org.opalj.br.ClassFile
import org.opalj.br.analyses.BasicReport
import org.opalj.br.instructions._
import util.ProjectInitializer

import scala.collection.mutable

/**
 * God Class Detector Analysis
 * Detects God Classes in Java bytecode based on configurable metrics:
 * - Weighted Methods per Class (WMC): Number of methods
 * - Tight Class Cohesion (TCC): Ratio of method pairs that share instance variables
 * - Access to Foreign Data (ATFD): Number of accesses to fields from other classes
 * - Number of Fields (NOF): Number of fields in the class
 */
class GodClassDetector extends SubAnalysis {

  override val logger: Logger = Logger("GodClassDetector")

  // Title of this Analysis
  val title: String = "God Class Detector"

  val analysisNumber: String = "1"

  // Configurable thresholds
  /** Weighted Methods per Class threshold */
  private var wmcThreshold: Int = -1
  /** Tight Class Cohesion threshold (lower values indicate potential God Class) */
  private var tccThreshold: Double = -1
  /** Access to Foreign Data threshold */
  private var atfdThreshold: Int = -1
  /** Number of Fields threshold */
  private var nofThreshold: Int = -1

  // Results storage
  /** The total amount of god classes found after analysis */
  private var godClassCount: Int = 0
  /** Results string to print after analysis */
  private val godClassDetails = new mutable.StringBuilder()


  override def executeAnalysis(config: StaticAnalysisConfig): Unit = {
    wmcThreshold = config.godClassDetector.wmcThresh
    tccThreshold = config.godClassDetector.tccThresh
    atfdThreshold = config.godClassDetector.atfdThresh
    nofThreshold = config.godClassDetector.nofThresh

    println()
    println(s"Looking for God Classes with thresholds:")
    println(s"- WMC (method count): $wmcThreshold")
    println(s"- TCC (cohesion): < $tccThreshold")
    println(s"- ATFD (foreign data access): > $atfdThreshold")
    println(s"- NOF (field count): $nofThreshold")
    println("--------------------------------------------------")

    println("Initializing project...")
    val project = ProjectInitializer.setupProject(cpFiles = config.projectJars, libcpFiles = config.libraryJars)
    val allClasses = project.allProjectClassFiles
    godClassCount = 0
    godClassDetails.clear()

    allClasses.foreach { classFile =>
      // Skip interfaces, abstract classes, and library classes
      if (!classFile.isInterfaceDeclaration && !classFile.isAbstract) {
        analyzeClass(classFile)
      }
    }

    println("\n==================== Results ====================")
    println(s"Analysis completed. Found $godClassCount God Class${if (godClassCount != 1) "es" else ""}.")

    // Return detailed report
    BasicReport(godClassDetails.toString)
  }


//  override def checkAnalysisSpecificParameters(parameters: Seq[String]): Iterable[String] = {
//    def getValue(arg: String): String = arg.substring(arg.indexOf("=") + 1)
//    if (parameters.isEmpty) {
//      return Nil
//    }
//    val issues: mutable.ListBuffer[String] = mutable.ListBuffer()
//    parameters.foreach {
//      case arg if arg.startsWith("-wmc=")  => getValue(arg).toIntOption match {
//        case Some(value) if value >= 0 => wmcThreshold = value
//        case Some(value) => issues += s"-wmc: Value must be non-negative, not $value"
//        case None => issues += s"-wmc: Value must be (non-negative) integer, not ${getValue(arg)}"
//      }
//      case arg if arg.startsWith("-tcc=")  => getValue(arg).toDoubleOption match {
//        case Some(value) if value >= 0 && value <= 1 => tccThreshold = value
//        case Some(value) => issues += s"-tcc: Value must be within range [0,1], not $value"
//        case None => issues += s"-tcc: Value must be decimal in range [0,1], not ${getValue(arg)}"
//      }
//      case arg if arg.startsWith("-atfd=") => getValue(arg).toIntOption match {
//        case Some(value) if value >= 0 => atfdThreshold = value
//        case Some(value) => issues += s"-atfd: Value must be non-negative, not $value"
//        case None => issues += s"-atfc: Value must be (non-negative) integer, not ${getValue(arg)}"
//      }
//      case arg if arg.startsWith("-nof=")  => getValue(arg).toIntOption match {
//        case Some(value) if value >= 0 => nofThreshold = value
//        case Some(value) => issues += s"-nof: Value must be non-negative, not $value"
//        case None => issues += s"-nof: Value must be (non-negative) integer, not ${getValue(arg)}"
//      }
//      case unknown => issues += s"unknown parameter: $unknown"
//    }
//    issues
//  }


//  override def analysisSpecificParametersDescription: String = super.analysisSpecificParametersDescription +
//    s"""[-wmc=<Integer> (Sets threshold for WMC (Weighted Methods per Class), default $wmcThreshold)]
//       |[-tcc=<Decimal in range [0,1]> (Sets threshold for TCC (Tight Class Cohesion), default $tccThreshold)]
//       |[-atfd=<Integer> (Sets threshold for ATFD (Access to Foreign Data), default $atfdThreshold)]
//       |[-nof=<Integer> (Sets threshold for NOF (Number of Fields), default $nofThreshold)]""".stripMargin


  /**
   * Analyze a single class to determine if it's a God Class
   */
  private def analyzeClass(classFile: ClassFile): Unit = {
    // Calculate metrics
    val wmc = calculateWMC(classFile)
    val nof = classFile.fields.size
    val tcc = calculateTCC(classFile)
    val atfd = calculateATFD(classFile)

    // Store metrics for reporting
    val metrics = Map(
      "WMC" -> wmc,
      "NOF" -> nof,
      "TCC" -> tcc,
      "ATFD" -> atfd
    )

    // Check if this class meets the criteria for a God Class
    if (isGodClass(metrics)) {
      godClassCount += 1

      // Build detailed information about this God Class
      godClassDetails.append(s"God Class: ${classFile.thisType.fqn}\n")
      godClassDetails.append(s"  WMC (methods): $wmc (threshold: $wmcThreshold)\n")
      godClassDetails.append(s"  NOF (fields): $nof (threshold: $nofThreshold)\n")
      godClassDetails.append(f"  TCC (cohesion): $tcc%.2f (threshold: < $tccThreshold)\n")
      godClassDetails.append(s"  ATFD (foreign data): $atfd (threshold: > $atfdThreshold)\n")
      godClassDetails.append("--------------------------------------------------\n")

      // Also print to console for immediate feedback
      println(s"Found God Class: ${classFile.thisType.fqn}")
      println(f"  WMC: $wmc, NOF: $nof, TCC: $tcc%.2f, ATFD: $atfd")
    }
  }


  /**
   * Calculate Weighted Methods per Class (WMC)
   * Basic implementation: counts the number of methods
   * Advanced implementation would consider method complexity
   */
  private def calculateWMC(classFile: ClassFile): Int = {
    // Count only concrete methods (no abstract methods)
    classFile.methods.count(m => !m.isAbstract)
  }


  /**
   * Calculate Tight Class Cohesion (TCC)
   * Measures the relative number of directly connected methods
   * Lower values indicate a potential God Class (methods don't share data)
   */
  private def calculateTCC(classFile: ClassFile): Double = {
    val methods = classFile.methods.filter(m => !m.isAbstract && !m.isConstructor)

    // If there are 0 or 1 methods, cohesion is perfect (1.0)
    if (methods.size <= 1) return 1.0

    // Map to track which methods access which fields
    val methodFieldAccesses = mutable.Map[Int, Set[String]]()

    // Determine which fields each method accesses
    methods.zipWithIndex.foreach { case (method, idx) =>
      val fieldAccesses = getAccessedFields(method)
      methodFieldAccesses(idx) = fieldAccesses
    }

    // Count method pairs that share at least one field
    var connectedPairs = 0
    val totalPairs = (methods.size * (methods.size - 1)) / 2

    for (i <- methods.indices; j <- (i+1) until methods.size) {
      val shared = methodFieldAccesses(i).intersect(methodFieldAccesses(j))
      if (shared.nonEmpty) {
        connectedPairs += 1
      }
    }

    // Calculate cohesion as the ratio of connected pairs to total possible pairs
    if (totalPairs == 0) 1.0 else connectedPairs.toDouble / totalPairs
  }


  /**
   * Get set of fields accessed by a method
   */
  private def getAccessedFields(method: org.opalj.br.Method): Set[String] = {
    // This is a simplified implementation
    // A real implementation would analyze the three-address code
    // and track all field accesses
    val accessedFields = mutable.Set[String]()

    if (method.body.isDefined) {
      val body = method.body.get
      body.instructions.filter(_ != null).foreach {
        case GETFIELD(declaringClass, name, _) =>
          accessedFields += s"${declaringClass.fqn}.$name"
        case PUTFIELD(declaringClass, name, _) =>
          accessedFields += s"${declaringClass.fqn}.$name"
        case _ => // Ignore other instructions
      }
    }

    accessedFields.toSet
  }


  /**
   * Calculate Access to Foreign Data (ATFD)
   * Counts how many times the class accesses attributes from other classes
   */
  private def calculateATFD(classFile: ClassFile): Int = {
    var foreignAccesses = 0
    val thisClassName = classFile.thisType.fqn

    // Check each method
    classFile.methods.foreach { method =>
      if (method.body.isDefined) {
        val body = method.body.get
        body.instructions.filter(_ != null).foreach {
          case GETFIELD(declaringClass, _, _) if declaringClass.fqn != thisClassName =>
            foreignAccesses += 1
          case PUTFIELD(declaringClass, _, _) if declaringClass.fqn != thisClassName =>
            foreignAccesses += 1
          case _ => // Ignore other instructions
        }
      }
    }

    foreignAccesses
  }


  /**
   * Determine if a class is a God Class based on its metrics
   */
  private def isGodClass(metrics: Map[String, Any]): Boolean = {
    // A class is considered a God Class if it meets at least 3 of the 4 criteria
    var criteriaCount = 0

    if (metrics("WMC").asInstanceOf[Int] >= wmcThreshold) criteriaCount += 1
    if (metrics("NOF").asInstanceOf[Int] >= nofThreshold) criteriaCount += 1
    if (metrics("TCC").asInstanceOf[Double] < tccThreshold) criteriaCount += 1
    if (metrics("ATFD").asInstanceOf[Int] > atfdThreshold) criteriaCount += 1

    criteriaCount >= 3
  }
}