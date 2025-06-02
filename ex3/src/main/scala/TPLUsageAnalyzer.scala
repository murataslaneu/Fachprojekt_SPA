import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, CallGraphKey, RTACallGraphKey, XTACallGraphKey}
import com.typesafe.config.{Config, ConfigFactory}
import org.opalj.log.{GlobalLogContext, LogContext, OPALLogger}
import analysis.{AnalysisConfig, JsonIO, TPLAnalysisResult, TPLMethodUsageAnalysis}

import java.io.File
import java.net.URL
import scala.jdk.CollectionConverters.MapHasAsJava

/**
 * Main entry point for running the TPL method usage analyzer.
 * This is the class that is invoked by SBT's runMain command.
 */
object TPLUsageAnalyzer extends Analysis[URL, BasicReport] with AnalysisApplication {

  // Stores config and runtime options
  private var config: Option[AnalysisConfig] = None
  private var callGraphAlgorithm: CallGraphKey = RTACallGraphKey
  private var callGraphAlgorithmName: String = "RTA"
  private var outputJsonFile: Option[String] = None
  private val program_begin = System.nanoTime()
  private implicit val logContext: GlobalLogContext.type = org.opalj.log.GlobalLogContext

  // Add a flag to check whether the user wants visual output or not
  private var visual: Boolean = false

  override def title: String = "Third Party Library Method Usage Analyzer (JSON-based)"

  /** Handles reading and validating command-line parameters, especially config file. */
  override def checkAnalysisSpecificParameters(parameters: Seq[String]): Iterable[String] = {
    val issues = scala.collection.mutable.ListBuffer[String]()

    parameters.foreach {
      case arg if arg.startsWith("-config=") =>
        val configPath = arg.substring(8)
        try {
          config = Some(JsonIO.readConfig(configPath))
          config.foreach { c =>
            callGraphAlgorithm = c.callGraphAlgorithm.toLowerCase match {
              case "cha" =>
                callGraphAlgorithmName = "CHA"
                CHACallGraphKey
              case "rta" =>
                callGraphAlgorithmName = "RTA"
                RTACallGraphKey
              case "xta" =>
                callGraphAlgorithmName = "XTA"
                XTACallGraphKey
              case "cfa" =>
                callGraphAlgorithmName = "1-1-CFA"
                CFA_1_1_CallGraphKey
              case "1-1-cfa" =>
                callGraphAlgorithmName = "1-1-CFA"
                CFA_1_1_CallGraphKey
              case "1_1_cfa" =>
                callGraphAlgorithmName = "1-1-CFA"
                CFA_1_1_CallGraphKey
              case "cfa_1_1" =>
                callGraphAlgorithmName = "1-1-CFA"
                CFA_1_1_CallGraphKey
              case "cfa-1-1" =>
                callGraphAlgorithmName = "1-1-CFA"
                CFA_1_1_CallGraphKey
              case _ =>
                issues += s"Unknown algorithm '${c.callGraphAlgorithm}'."
                RTACallGraphKey
            }
            outputJsonFile = c.outputJson
          }
        } catch {
          case ex: Exception => issues += s"Error reading config file: ${ex.getMessage}"
        }

      // Set the visual flag if -visual parameter is present
      case "-visual" =>
        visual = true

      case unknown =>
        issues += s"Unknown parameter: $unknown"
    }

    if (config.isEmpty) issues += "-config: Missing. Please provide a config file with -config=config.json"
    issues
  }

  override def analysisSpecificParametersDescription: String = {
    """ ========================= REQUIRED PARAMETER =========================
      | [-config=<config.json> (Configuration used for analysis. See template for schema.)]
      |
      | This analysis uses a custom config json to configure the project.
      | OPTIONS -cp AND -libcp ARE IGNORED. PLEASE CONFIGURE PROJECT
      | AND LIBRARY JARS VIA THE CONFIG JSON.""".stripMargin
  }

  /**
   * Loads the target project and all TPLs (JARs) as class files for analysis.
   * Makes sure libraries are loaded as full implementations, not just as interfaces.
   */
  override def setupProject(cpFiles: Iterable[File], libcpFiles: Iterable[File], completelyLoadLibraries: Boolean, configuredConfig: Config)
                           (implicit initialLogContext: LogContext): Project[URL] = {

    val projectFiles = config.map(c => List(new File(c.projectJar))).getOrElse(Nil)
    val allLibs = config.map(_.tplJars.map(new File(_))).getOrElse(Nil)

    println(s"Project files: ${projectFiles.map(_.getName)}")
    println(s"Library files: ${allLibs.map(_.getName)}")

    // Very important: Load real library implementations, not just interfaces!
    if (config.get.isLibraryProject) {
      val overrides = ConfigFactory.parseMap(Map(
        "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis" ->
          "org.opalj.br.analyses.cg.LibraryEntryPointsFinder",
        "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis" ->
          "org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder"
      ).asJava)
      val newConfig = overrides.withFallback(configuredConfig).resolve()
      super.setupProject(projectFiles, allLibs, completelyLoadLibraries = true, newConfig)
    }
    else {
      super.setupProject(projectFiles, allLibs, completelyLoadLibraries = true, configuredConfig)
    }

  }

  /**
   * Main analysis logic: Build call graph, run TPL method analysis, report results.
   */
  override def analyze(project: Project[URL], parameters: Seq[String], initProgressManagement: Int => ProgressManagement): BasicReport = {
    val tplFiles = config.map(_.tplJars.map(new File(_))).getOrElse(Nil)

    // Get call graph (as returned by OPAL using selected algorithm)
    OPALLogger.info("Progress", s"Start computing $callGraphAlgorithmName call graph...")
    val callGraph_begin = System.nanoTime()
    val callGraph = project.get(callGraphAlgorithm)
    val callGraph_end = System.nanoTime()
    val callGraphTime = BigDecimal((callGraph_end - callGraph_begin) / 1e9).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
    OPALLogger.info("Progress", s"Call graph computed in $callGraphTime seconds")

    // Analyze call graph on which third party library methods have been used
    OPALLogger.info("Progress", s"Beginning analysis on call graph...")
    val analysis_begin = System.nanoTime()
    val result = TPLMethodUsageAnalysis.analyze(
      project,
      callGraph,
      tplFiles,
      config.get
    ).sortBy {tplInfo => tplInfo.library}
    val analysis_end = System.nanoTime()
    val analysisTime = BigDecimal((analysis_end - analysis_begin) / 1e9).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
    val programTime = BigDecimal((analysis_end - program_begin) / 1e9).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
    OPALLogger.info("Progress", s"Analysis finished in $analysisTime seconds, results computed")
    OPALLogger.info("Progress", s"Program finished in $programTime seconds, outputting results...")

    val finalResult = TPLAnalysisResult(result, callGraphAlgorithmName, callGraphTime, analysisTime, programTime)

    // Output results to file if wanted
    outputJsonFile match {
      case Some(path) =>
        JsonIO.writeResult(finalResult, path)
        OPALLogger.info("Progress", s"Result written to $path\n")
      case None => // Do nothing
    }

    // Build output string for console
    val analysisResults = new StringBuilder()
    analysisResults.append("==================== RESULTS ====================\n")
    analysisResults.append("Note: The number of used methods also contains indirectly called methods from the library.\n")
    if (!config.get.countAllMethods) analysisResults.append("Total number and number of used methods only contain public methods. (default)\n")
    else analysisResults.append("NOTE: Flag countAllMethods active. Counted every method, even those with non-public visibility.\n")
    result.foreach { libraryResults =>
      analysisResults.append(s"${libraryResults.library}:\n")
      analysisResults.append(s"    - Total: ${libraryResults.totalMethods} methods\n")
      analysisResults.append(s"    - Used:  ${libraryResults.usedMethods} methods\n")
      analysisResults.append(s"    - Usage ratio: ${libraryResults.usageRatio}\n")
    }
    analysisResults.append("==================== RUN TIMES ====================\n")
    analysisResults.append(s"Computing $callGraphAlgorithmName call graph: $callGraphTime seconds\n")
    analysisResults.append(s"Analysis on call graph: $analysisTime seconds\n")
    analysisResults.append(s"Run time of entire program: $programTime seconds\n")

    // If visual output is requested, launch the TPLUsageVisualizer after analysis is complete
    if (visual) {
      val resultFile = outputJsonFile.getOrElse("result.json")
      try {
        // Directly call the visualizer's utility function in the same JVM process
        visualization.TPLUsageVisualizer.showChart(resultFile)
      } catch {
        case e: Exception =>
          println(s"[ERROR] Visualizer could not be shown: ${e.getMessage}")
      }
    }

    BasicReport(analysisResults.toString)
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}