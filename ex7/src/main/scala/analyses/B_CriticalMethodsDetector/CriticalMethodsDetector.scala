package analyses.B_CriticalMethodsDetector

import analyses.SubAnalysis
import analysis.CriticalMethodsAnalysis
import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, CTACallGraphKey, RTACallGraphKey, XTACallGraphKey}
import org.slf4j.MarkerFactory
import util.{ProjectInitializer, Utils}

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
    var ignoreClassString = Random.shuffle(analysisConfig.ignore).take(10).map {ignoreCall =>
      s"${ignoreCall.callerClass}#${ignoreCall.callerMethod} -> ${ignoreCall.targetClass}#${ignoreCall.targetMethod}"
    }.mkString("\n    - ", "\n    - ", "")
    val moreIgnoreCalls = if(analysisConfig.ignore.size > 10) s"\n... and ${analysisConfig.ignore.size - 10} more ignores"
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
    val tuple = CriticalMethodsAnalysis.analyze(callGraph, analysisConfig)

    val results = tuple._1
    val ignoredAtLeastOneCall = tuple._2
    val analysisResults = new StringBuilder()
    if (results.nonEmpty) {
      results.foreach { result =>
        analysisResults.append(result + "\n")
      }
      if (ignoredAtLeastOneCall) analysisResults.append("Other method calls have been found but were ignored due to the config.\n")
    } else {
      if (ignoredAtLeastOneCall) analysisResults.append("Method calls have been found but were ignored due to the config.\n")
    }

    logger.info(s"Analysis completed. Found ${results.length} critical call${if (results.length != 1) "s" else ""}.")
    logger.info(s"Results:\n${analysisResults.toString}")
  }
}
