package analyses.E_DeadCodeDetector

import analyses.SubAnalysis
import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig
import helpers.{DeadCodeAnalysis, JsonIO}
import util.{ProjectInitializer, Utils}

import java.nio.file.{Files, Path, Paths}

/**
 * Application that searches through the project for dead instructions
 * (i.e. instructions that will under no circumstances be executed during runtime)
 */
class DeadCodeDetector(override val shouldExecute: Boolean) extends SubAnalysis {

  /** Logger used inside this sub-analysis */
  override val logger: Logger = Logger("DeadCodeDetector")
  /** The name of the sub-analysis */
  override val analysisName: String = "Dead Code Detector"
  /** The number of the sub-analysis */
  override val analysisNumber: String = "5"
  /** Name of the folder where this sub-analysis will put their results in */
  override val outputFolderName: String = "5_DeadCodeDetector"

  override def executeAnalysis(config: StaticAnalysisConfig): Unit = {
    val analysisConfig = config.deadCodeDetector

    // Print out configuration
    logger.info(
      s"""Configuration:
         |  - Completely load libraries: ${analysisConfig.completelyLoadLibraries}""".stripMargin
    )

    // Set up project
    logger.info("Initializing OPAL project...")
    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = config.projectJars,
      libcpFiles = config.libraryJars,
      completelyLoadLibraries = analysisConfig.completelyLoadLibraries,
    )
    logger.info("Project initialization finished. Starting analysis on project...")

    // Domain selection removed to avoid user input during analysis
    // Instead, run all domains

    val (multiDomainReport, singleDomainReports) = DeadCodeAnalysis.analyze(logger, project, config)
    logger.info("Analysis finished. Writing reports...")
    val outputDir = s"${config.resultsOutputPath}/$outputFolderName"
    val multiDomainReportPath = s"$outputDir/multiDomainResult.json"
    JsonIO.writeMultiDomainResult(multiDomainReport, multiDomainReportPath)
    val singleDomainReportDirectory = s"$outputDir/singleDomainReports"
    val singleDomainReportPath = Path.of(singleDomainReportDirectory)
    if (Files.notExists(singleDomainReportPath)) {
      Files.createDirectory(singleDomainReportPath)
    }
    singleDomainReports.zipWithIndex.foreach { case (report, i) =>
      val singleDomainReportPath = s"$singleDomainReportDirectory/$i.json"
      JsonIO.writeSingleDomainResult(report, singleDomainReportPath)
    }
    logger.info(s"Reports written to $outputDir.")
  }
}
