package analyses.E_DeadCodeDetector

import analyses.SubAnalysis
import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig
import helpers.{DeadCodeAnalysis, JsonIO}
import org.opalj.ai.common.DomainRegistry
import util.ProjectInitializer

import java.nio.file.{Files, Path}

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

    val domainsString = buildSelectedDomainsString(analysisConfig.domains)

    // Print out configuration
    logger.info(
      s"""Configuration:
         |  - Completely load libraries: ${analysisConfig.completelyLoadLibraries}
         |  - Selected domains:$domainsString""".stripMargin
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
    singleDomainReports.foreach { case (i, report) =>
      val singleDomainReportPath = s"$singleDomainReportDirectory/$i.json"
      JsonIO.writeSingleDomainResult(report, singleDomainReportPath)
    }
    logger.info(s"Reports written to $outputDir.")
  }

  /**
   * Builds a string out of the given domains list from the config
   * and prints the configuration out in a more readable way.
   *
   * Also checks whether the given domain numbers are valid.
   *
   * @param domains List of domain numbers specified in the config.
   * @return String that can be logged to show the configuration.
   *
   * @throws IllegalArgumentException When a given domain number is not available (i.e. outside of the range 1 to 13
   *                                  (inclusive) for OPAL 5.0.0). Gets also thrown when domains is empty.
   */
  private def buildSelectedDomainsString(domains: List[Int]): String = {
    // Retrieve all available domain names and sort them
    val domainNames = DomainRegistry
      .domainDescriptions()
      .map { domain => domain.substring(domain.indexOf("[") + 1, domain.indexOf("]")) }
      .toList
      .sorted
    // Index domains such that the indexes from the config can be used to access these domains
    val domainNamesIndexed = (1 to domainNames.length).toList.map { i =>
      i -> domainNames(i-1)
    }.toMap

    if (domains.isEmpty) {
      throw new IllegalArgumentException(
        s"List for \"deadCodeDetector\" \\ \"domains\" is empty. " +
          s"Expected list of integers with a value of at least 1 and at most ${domainNames.length}."
      )
    }

    val stringBuilder = new StringBuilder()
    domains.distinct.foreach { domainNumber: Int =>
      if (domainNumber < 1 || domainNumber > domainNames.length) {
        throw new IllegalArgumentException(
          s"Domain number $domainNumber given in \"deadCodeDetector\" \\ \"domains\" is outside of the valid range" +
          s"(must be at least 1 and at most ${domainNames.length}")
      }
      stringBuilder.append(s"\n    - [$domainNumber]${if (domainNumber < 10) " " else ""} ${domainNamesIndexed(domainNumber)}")
    }

    stringBuilder.toString
  }
}
