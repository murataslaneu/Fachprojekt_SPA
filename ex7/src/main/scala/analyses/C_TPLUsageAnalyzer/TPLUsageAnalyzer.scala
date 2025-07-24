package analyses.C_TPLUsageAnalyzer

import analyses.SubAnalysis
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, CTACallGraphKey, RTACallGraphKey, XTACallGraphKey}
import analysis.{JsonIO, TPLAnalysisResult, TPLMethodUsageAnalysis}
import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig
import org.knowm.xchart.BitmapEncoder.BitmapFormat
import org.knowm.xchart.{BitmapEncoder, CategoryChart, CategoryChartBuilder}
import org.slf4j.MarkerFactory
import util.{ProjectInitializer, Utils}

import java.io.File
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.Random

/**
 * Main entry point for running the TPL method usage analyzer.
 * This is the class that is invoked by SBT's runMain command.
 */
class TPLUsageAnalyzer(override val shouldExecute: Boolean) extends SubAnalysis {

  /** Logger used inside this sub-analysis */
  override val logger: Logger = Logger("TPLUsageAnalyzer")
  /** The name of the sub-analysis */
  override val analysisName: String = "Third Party Library Usage Analyzer"
  /** The number of the sub-analysis */
  override val analysisNumber: String = "3"
  /** Name of the folder where this sub-analysis will put their results in */
  override val outputFolderName: String = "3_TPLUsageAnalyzer"

  /**
   * Main analysis logic: Build call graph, run TPL method analysis, report results.
   */
  override def executeAnalysis(config: StaticAnalysisConfig): Unit = {
    val subAnalysis_begin = System.currentTimeMillis()
    val analysisConfig = config.tplUsageAnalyzer
    val callGraphAlgorithmName = analysisConfig.callGraphAlgorithmName.toUpperCase

    // Print out configuration
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
         |  - Count all methods: ${analysisConfig.countAllMethods}
         |  - Call graph algorithm: $callGraphAlgorithmName
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
    val callGraphKey = callGraphAlgorithmName match {
      case "CHA" => CHACallGraphKey
      case "RTA" => RTACallGraphKey
      case "XTA" => XTACallGraphKey
      case "CTA" => CTACallGraphKey
      case "1-1-CFA" => CFA_1_1_CallGraphKey
    }
    logger.info("Project initialization finished. Starting analysis on project...")

    // Get call graph (as returned by OPAL using selected algorithm)
    logger.info(
      MarkerFactory.getMarker("BLUE"),
      s"Beginning calculation of the $callGraphAlgorithmName call graph..."
    )
    logger.info(
      MarkerFactory.getMarker("BLUE"),
      s"(This might take a while, depending on the call graph algorithm and size of the project and libraries!)"
    )
    val callGraph_begin = System.currentTimeMillis()
    val callGraph = project.get(callGraphKey)
    val callGraph_end = System.currentTimeMillis()
    logger.info(s"Finished calculation of the $callGraphAlgorithmName call graph.")

    // Analyze call graph on which third party library methods have been used
    logger.info("Beginning analysis on the call graph...")
    val analysis_begin = System.currentTimeMillis()
    val result = TPLMethodUsageAnalysis.analyze(project, callGraph, config.libraryJars, analysisConfig)
      .sortBy { tplInfo => tplInfo.library }
    val analysis_end = System.currentTimeMillis()

    // Calculate runtimes
    val callGraphTime = BigDecimal((callGraph_end - callGraph_begin) / 1000.0).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
    val analysisTime = BigDecimal((analysis_end - analysis_begin) / 1000.0).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
    val subAnalysisTime = BigDecimal((analysis_end - subAnalysis_begin) / 1000.0).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
    val finalResult = TPLAnalysisResult(
      config.projectJars.map { file => file.getPath.replace('\\', '/') },
      result,
      analysisConfig.callGraphAlgorithmName.toUpperCase,
      callGraphTime,
      analysisTime,
      subAnalysisTime
    )

    // Build output string for log
    val analysisResults = new StringBuilder()
    analysisResults.append("Note: The number of used methods also contains indirectly called methods from the library.\n")
    if (!analysisConfig.countAllMethods) analysisResults.append("Total number and number of used methods only contain public methods. (default)\n")
    else analysisResults.append("NOTE: Flag countAllMethods active. Counted every method, even those with non-public visibility.\n")
    val k = 5
    Random.shuffle(result)
      .take(k)
      .sortBy { info => info.library }
      .foreach { libraryResults =>
        analysisResults.append(s"${libraryResults.library}:\n")
        analysisResults.append(s"  - Total: ${libraryResults.totalMethods} methods\n")
        analysisResults.append(s"  - Used:  ${libraryResults.usedMethods} methods\n")
        analysisResults.append(s"  - Usage ratio: ${libraryResults.usageRatio}\n")
      }
    if (result.size > k) analysisResults.append(s"\n... and ${result.length - k} more library jars\n")
    analysisResults.append("Run times:\n")
    analysisResults.append(s"Computing $callGraphAlgorithmName call graph: $callGraphTime seconds\n")
    analysisResults.append(s"Analysis on call graph: $analysisTime seconds\n")
    analysisResults.append(s"Run time of entire sub-analysis: $subAnalysisTime seconds")

    // Output results
    val outputDirectory = s"${config.resultsOutputPath}/$outputFolderName"
    val jsonOutputPath = s"$outputDirectory/results.json"
    JsonIO.writeResult(finalResult, jsonOutputPath)
    exportChart(finalResult, outputDirectory)
    logger.info(s"Wrote results to $outputDirectory.")

    logger.info(s"Results:\n$analysisResults")
  }

  /**
   * Create a chart of TPL coverage using the TPLAnalysisResult.
   *
   * @param results The results generated by this analysis.
   */
  private def exportChart(results: TPLAnalysisResult, outputDirectory: String): Unit = {
    // Extract library names, usage percentages, and method counts
    val libraries = results.analysis.map { tplInfo =>
      val jarName = tplInfo.library.substring(tplInfo.library.lastIndexOf("/") + 1)
      if (jarName.length <= 30) jarName
      else jarName.substring(0, 30) + "..."
    }
    // Skip visualization when nothing can be visualized to avoid exception
    if (libraries.isEmpty) {
      val chartFile = new File(s"$outputDirectory/chart.png")
      if (chartFile.exists) {
        chartFile.delete()
      }
      return
    }
    val usagePercents: java.util.List[java.lang.Double] =
      results.analysis.map { tplInfo => java.lang.Double.valueOf(tplInfo.usageRatio * 100) }.asJava

    // Create a bar chart to visualize the coverage for each library
    val chart: CategoryChart = new CategoryChartBuilder()
      .width(1350)
      .height(750)
      .title(s"TPL Usage (${results.callGraphAlgorithm})")
      .xAxisTitle("Library")
      .yAxisTitle("Coverage (%)")
      .build()

    // Add the coverage series to the chart
    chart.addSeries("Coverage", libraries.asJava, usagePercents)
    chart.getStyler.setLegendVisible(false) // No legend
    chart.getStyler.setXAxisLabelRotation(60) // Rotate labels

    BitmapEncoder.saveBitmap(chart, s"$outputDirectory/chart", BitmapFormat.PNG)
  }
}