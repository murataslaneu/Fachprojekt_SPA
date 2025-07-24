package analyses.A_GodClassDetector

import analyses.A_GodClassDetector.data.{GodClass, JsonReport}
import analyses.SubAnalysis
import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig
import org.opalj.br.ClassFile
import org.opalj.br.analyses.Project
import org.opalj.br.instructions._
import play.api.libs.json.Json
import util.ProjectInitializer

import java.io.{File, PrintWriter}
import java.net.URL
import scala.collection.mutable
import scala.util.Random

/**
 * God Class Detector Analysis
 *
 * Detects God Classes in Java bytecode based on configurable metrics:
 * - Weighted Methods per Class (WMC): Number of methods
 * - Tight Class Cohesion (TCC): Ratio of method pairs that share instance variables
 * - Access to Foreign Data (ATFD): Number of accesses to fields from other classes
 * - Number of Fields (NOF): Number of fields in the class
 */
class GodClassDetector(override val shouldExecute: Boolean) extends SubAnalysis {

  /** Logger used inside this sub-analysis */
  override val logger: Logger = Logger("GodClassDetector")
  /** The name of the sub-analysis */
  override val analysisName: String = "God Class Detector"
  /** The number of the sub-analysis */
  override val analysisNumber: String = "1"
  /** Name of the folder where this sub-analysis will put their results in */
  override val outputFolderName: String = "1_GodClassDetector"

  override def executeAnalysis(config: StaticAnalysisConfig): Unit = {
    val wmcThreshold = config.godClassDetector.wmcThresh
    if (wmcThreshold < 0)
      throw new IllegalArgumentException(s"wmcThresh must be non-negative integer, received $wmcThreshold.")
    val tccThreshold = config.godClassDetector.tccThresh
    if (tccThreshold < 0 || tccThreshold > 1)
      throw new IllegalArgumentException(s"tccThresh must be decimal number between 0 and 1, received $tccThreshold.")
    val atfdThreshold = config.godClassDetector.atfdThresh
    if (atfdThreshold < 0)
      throw new IllegalArgumentException(s"atfdThresh must be non-negative integer, received $atfdThreshold.")
    val nofThreshold = config.godClassDetector.nofThresh
    if (nofThreshold < 0)
      throw new IllegalArgumentException(s"nofThresh must be non-negative integer, received $nofThreshold.")

    logger.info(
      s"""Configuration (looking for god classes with the following thresholds):
         |  - WMC (Weighted Methods per Class) >= $wmcThreshold
         |  - TCC (Tight Class Cohesion) < $tccThreshold
         |  - ATFD (Access to Foreign Data) > $atfdThreshold
         |  - NOF (Number of Fields) >= $nofThreshold""".stripMargin)
    logger.info("Initializing OPAL project...")
    val project = ProjectInitializer.setupProject(cpFiles = config.projectJars, libcpFiles = config.libraryJars, logger = logger)
    logger.info("Project initialization finished. Starting analysis on project...")

    val allClasses = project.allProjectClassFiles

    val godClasses = mutable.ListBuffer[GodClass]()

    allClasses.foreach { classFile =>
      // Skip interfaces, abstract classes, and library classes
      if (!classFile.isInterfaceDeclaration && !classFile.isAbstract) {
        val maybeGodClass = analyzeClass(project, classFile, wmcThreshold, tccThreshold, atfdThreshold, nofThreshold)
        if (maybeGodClass.isDefined) godClasses += maybeGodClass.get
      }
    }

    logger.info("Analysis finished.")

    val godClassCount = godClasses.length

    val report = JsonReport(
      projectJars = config.projectJars.map { file => file.getPath.replace('\\', '/') },
      wmcThreshold = wmcThreshold,
      tccThreshold = tccThreshold,
      atfdThreshold = atfdThreshold,
      nofThreshold = nofThreshold,
      godClasses = godClasses.toList
    )
    val outputDirectory = s"${config.resultsOutputPath}/$outputFolderName"
    val jsonOutputPath = s"$outputDirectory/results.json"
    writeJsonReport(report, jsonOutputPath)
    logger.info(s"Wrote json report to $jsonOutputPath.")

    if (godClassCount == 0) {
      logger.info(s"Found no god classes.")
    }
    else {
      val godClassDetails = buildGodClassDetailsString(godClasses, 10)
      logger.info(s"Found $godClassCount god class${if (godClassCount != 1) "es" else ""}: $godClassDetails")
    }
  }

  /**
   * Analyze a single class to determine if it's a god class.
   *
   * @return A GodClass when the classFile contains a god class, else None
   */
  private def analyzeClass(project: Project[URL], classFile: ClassFile, wmcThreshold: Int, tccThreshold: Double, atfdThreshold: Int, nofThreshold: Int): Option[GodClass] = {
    // Calculate metrics
    val wmc = calculateWMC(classFile)
    val nof = classFile.fields.size
    val tcc = calculateTCC(classFile)
    val atfd = calculateATFD(classFile)

    // Compare metrics and thresholds
    val metrics: Map[String, Number] = Map(
      "WMC" -> wmc,
      "TCC" -> tcc,
      "ATFD" -> atfd,
      "NOF" -> nof
    )
    val thresholds: Map[String, Number] = Map(
      "WMC" -> wmcThreshold,
      "TCC" -> tccThreshold,
      "ATFD" -> atfdThreshold,
      "NOF" -> nofThreshold
    )

    // Check if this class meets the criteria for a God Class
    if (isGodClass(metrics, thresholds)) {
      val jarFile = project.source(classFile) match {
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

      Some(GodClass(
        className = classFile.thisType.toJava,
        jar = jarFile,
        wmc = wmc,
        tcc = tcc,
        atfd = atfd,
        nof = nof
      ))
    }
    else None
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

    for (i <- methods.indices; j <- (i + 1) until methods.size) {
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
  private def isGodClass(metrics: Map[String, Number], thresholds: Map[String, Number]): Boolean = {
    // A class is considered a God Class if it meets at least 3 of the 4 criteria
    var criteriaCount = 0

    if (metrics("WMC").intValue() >= thresholds("WMC").intValue()) criteriaCount += 1
    if (metrics("TCC").doubleValue() < thresholds("TCC").doubleValue()) criteriaCount += 1
    if (metrics("ATFD").intValue() > thresholds("ATFD").intValue()) criteriaCount += 1
    if (metrics("NOF").intValue() >= thresholds("NOF").intValue()) criteriaCount += 1

    criteriaCount >= 3
  }

  /**
   * Builds a string that can be used to print some sample god classes in the logs.
   *
   * Also sorts the samples alphabetically.
   *
   * @param godClasses God classes found by the analysis.
   * @param k          The number of samples to show. When godClasses contains less than k elements, just show all elements.
   * @return String that can be outputted in the logs.
   */
  //noinspection SameParameterValue
  private def buildGodClassDetailsString(godClasses: mutable.ListBuffer[GodClass], k: Int): String = {
    val samples = Random.shuffle(godClasses).take(k).toList
    if (samples.isEmpty) return "None"
    val mainString = samples.map { sample =>
      val className = sample.className
      val wmc = sample.wmc
      val tcc = f"${sample.tcc}%.2f".replace(',', '.')
      val atfd = sample.atfd
      val nof = sample.nof
      f"$className: WMC $wmc, TCC $tcc, ATFD $atfd, NOF $nof"
    }.sorted.mkString("\n  - ", "\n  - ", "")

    val remainingClasses = godClasses.length - k
    val moreClasses = if (remainingClasses > 0) s"\n... and $remainingClasses more god class${if (remainingClasses != 1) "es" else ""}"
    else ""

    s"$mainString$moreClasses"
  }

  /** Writes the analysis result to a file in JSON format */
  private def writeJsonReport(report: JsonReport, path: String): Unit = {
    val writer = new PrintWriter(new File(path))
    writer.write(Json.prettyPrint(Json.toJson(report)))
    writer.close()
  }
}