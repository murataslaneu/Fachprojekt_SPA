package analyses.B_CriticalMethodsDetector

import data.{CriticalCall, JsonReport}
import analyses.SubAnalysis
import analysis.CriticalMethodsAnalysis
import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, CTACallGraphKey, RTACallGraphKey, XTACallGraphKey}
import org.slf4j.MarkerFactory
import play.api.libs.json.Json
import util.{ProjectInitializer, Utils}

import java.io.{File, PrintWriter}
import scala.util.Random


/**
 * Application that looks for possibly critical method calls in a software project regarding security.
 */
class CriticalMethodsDetector(override val shouldExecute: Boolean) extends SubAnalysis {

  /** Logger used inside this sub-analysis */
  override val logger: Logger = Logger("CriticalMethodsDetector")
  /** The name of the sub-analysis */
  override val analysisName: String = "Critical Methods Detector"
  /** The number of the sub-analysis */
  override val analysisNumber: String = "2"
  /** Name of the folder where this sub-analysis will put their results in */
  override val outputFolderName: String = "2_CriticalMethodsDetector"

  override def executeAnalysis(config: StaticAnalysisConfig): Unit = {
    val analysisConfig = config.criticalMethodsDetector
    // Print out configuration
    val criticalMethodsString = Utils.buildSampleSelectedMethodsString(analysisConfig.criticalMethods, 5, 3)
    var ignoreClassString = Random.shuffle(analysisConfig.ignore).take(10).map { ignoreCall =>
      s"${ignoreCall.callerClass}#${ignoreCall.callerMethod} -> ${ignoreCall.targetClass}#${ignoreCall.targetMethod}"
    }.mkString("\n    - ", "\n    - ", "")
    val moreIgnoreCalls = if (analysisConfig.ignore.size > 10) s"\n... and ${analysisConfig.ignore.size - 10} more ignores"
    else ""
    // Override ignoreClassString if it doesn't contain anything
    if (analysisConfig.ignore.isEmpty) ignoreClassString = "None"
    val entryPointsFinder = analysisConfig.entryPointsFinder match {
      case "custom" => "Only custom entry points"
      case "application" => "Application (without JRE)"
      case "applicationwithjre" => "Application with JRE"
      case "library" => "Library"
      case invalid => throw new IllegalArgumentException(s"Invalid entry points finder $invalid selected.")
    }
    val customEntryPointsString = Utils.buildSampleSelectedMethodsString(analysisConfig.customEntryPoints, 5, 3)
    logger.info(
      s"""Configuration:
         |  - Critical Methods: $criticalMethodsString
         |  - Ignore calls: $ignoreClassString$moreIgnoreCalls
         |  - Call graph algorithm: ${analysisConfig.callGraphAlgorithmName.toUpperCase}
         |  - Entry points finder: $entryPointsFinder
         |  - Custom entry points: $customEntryPointsString""".stripMargin
    )

    // Set up project
    logger.info("Initializing OPAL project...")
    val opalConfig = ProjectInitializer.setupOPALProjectConfig(analysisConfig.entryPointsFinder, analysisConfig.customEntryPoints)
    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = config.projectJars,
      libcpFiles = config.libraryJars,
      completelyLoadLibraries = true,
      configuredConfig = opalConfig
    )

    val callGraphKey = analysisConfig.callGraphAlgorithmName.toUpperCase match {
      case "CHA" => CHACallGraphKey
      case "RTA" => RTACallGraphKey
      case "XTA" => XTACallGraphKey
      case "CTA" => CTACallGraphKey
      case "1-1-CFA" => CFA_1_1_CallGraphKey
    }

    logger.info("Project initialization finished. Starting analysis on project...")
    logger.info(
      MarkerFactory.getMarker("BLUE"),
      s"Beginning calculation of the ${analysisConfig.callGraphAlgorithmName.toUpperCase} call graph..."
    )
    logger.info(
      MarkerFactory.getMarker("BLUE"),
      s"(This might take a while, depending on the call graph algorithm and size of the project and libraries!)"
    )
    val callGraph = project.get(callGraphKey)
    logger.info(s"Finished calculation of the ${analysisConfig.callGraphAlgorithmName.toUpperCase} call graph.")

    logger.info("Beginning analysis on the call graph...")
    val (criticalCalls, ignoredAtLeastOneCall) = CriticalMethodsAnalysis.analyze(callGraph, analysisConfig)
    logger.info("Analysis completed.")

    val report = JsonReport(
      projectJars = config.projectJars.map { file => file.getPath.replace('\\', '/') },
      libraryJars = config.libraryJars.map { file => file.getPath.replace('\\', '/') },
      criticalMethods = analysisConfig.criticalMethods,
      ignore = analysisConfig.ignore,
      callGraphAlgorithmUsed = analysisConfig.callGraphAlgorithmName.toUpperCase,
      usedEntryPointsFinder = entryPointsFinder,
      customEntryPoints = analysisConfig.customEntryPoints,
      criticalCallsFound = criticalCalls.length,
      criticalCalls = criticalCalls
    )

    val outputDirectory = s"${config.resultsOutputPath}/$outputFolderName"
    val jsonOutputPath = s"$outputDirectory/results.json"
    writeJsonReport(report, jsonOutputPath)
    logger.info(s"Wrote json report to $jsonOutputPath.")

    if (criticalCalls.isEmpty) {
      logger.info("Found no critical calls.")
      if (ignoredAtLeastOneCall) {
        logger.info("Method calls have been found but were ignored due to the config.")
      }
    }
    else {
      val resultsString = buildCriticalCallsString(criticalCalls, 10)
      logger.info(s"Found ${criticalCalls.length} critical call${if (criticalCalls.length != 1) "s" else ""}: $resultsString")
      if (ignoredAtLeastOneCall) {
        logger.info("Other method calls have been found but were ignored due to the config.")
      }
    }
  }

  /** Writes the analysis result to a file in JSON format */
  private def writeJsonReport(report: JsonReport, path: String): Unit = {
    val writer = new PrintWriter(new File(path))
    writer.write(Json.prettyPrint(Json.toJson(report)))
    writer.close()
  }

  /**
   * Builds a string that can be used to print some sample critical calls in the logs.
   *
   * Also sorts the samples alphabetically.
   *
   * @param criticalCalls CriticalCalls found by the analysis.
   * @param k          The number of samples to show. When criticalCalls contains less than k elements, just show all elements.
   * @return String that can be outputted in the logs.
   */
  //noinspection SameParameterValue
  private def buildCriticalCallsString(criticalCalls: List[CriticalCall], k: Int): String = {
    val samples = Random.shuffle(criticalCalls).take(k)
    if (samples.isEmpty) return "None"
    val mainString = samples.map { sample =>
      val from = s"${sample.fromClass}#${sample.fromMethod}"
      val to = s"${sample.toClass}#${sample.toMethod}"
      val count = sample.numberOfCalls
      s"""  - Critical call of $to
         |    - From: $from
         |    - Number of calls: $count""".stripMargin
    }.sorted.mkString("\n", "\n", "")
    val remainingCalls = criticalCalls.length - k
    val moreClasses = if (remainingCalls > 0) s"\n... and $remainingCalls more critical call${if (remainingCalls != 1) "s" else ""}"
    else ""

    s"$mainString$moreClasses"
  }
}
