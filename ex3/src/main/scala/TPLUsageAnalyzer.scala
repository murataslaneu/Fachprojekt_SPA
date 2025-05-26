import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.tac.cg.{CHACallGraphKey, CTACallGraphKey, CallGraphKey, RTACallGraphKey, XTACallGraphKey}
import com.typesafe.config.Config
import org.opalj.log.LogContext
import analysis.{AnalysisConfig, TPLMethodUsageAnalysis, JsonIO}

import java.io.File
import java.net.URL

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

  override def title: String = "Third Party Library Method Usage Analyzer (JSON-based)"

  /** Handles reading and validating command-line parameters, especially config file. */
  override def checkAnalysisSpecificParameters(parameters: Seq[String]): Iterable[String] = {
    val issues = scala.collection.mutable.ListBuffer[String]()

    parameters.foreach {
      case arg if arg.startsWith("-config=") =>
        val configPath = arg.substring(8)
        try {
          val jsonStr = scala.io.Source.fromFile(configPath).mkString
          config = Some(play.api.libs.json.Json.parse(jsonStr).as[AnalysisConfig])
          config.foreach { c =>
            callGraphAlgorithmName = c.callGraphAlgorithm.toUpperCase
            callGraphAlgorithm = c.callGraphAlgorithm.toLowerCase match {
              case "cha" => CHACallGraphKey
              case "rta" => RTACallGraphKey
              case "xta" => XTACallGraphKey
              case "cta" => CTACallGraphKey
              case _ =>
                issues += s"Unknown algorithm '${c.callGraphAlgorithm}'."
                RTACallGraphKey
            }
            outputJsonFile = c.outputJson
          }
        } catch {
          case ex: Exception => issues += s"Error reading config file: ${ex.getMessage}"
        }

      case unknown =>
        issues += s"Unknown parameter: $unknown"
    }

    if (config.isEmpty) issues += "You must provide a config file using -config=yourfile.json"
    issues
  }

  override def analysisSpecificParametersDescription: String =
    "[REQUIRED] -config=<config.json>   (see template for schema)"

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

    // Very important: load real library implementations, not just interfaces!
    super.setupProject(projectFiles, allLibs, completelyLoadLibraries = true, configuredConfig)
  }

  /**
   * Main analysis logic: builds the call graph, runs TPL method analysis, and reports results.
   */
  override def analyze(project: Project[URL], parameters: Seq[String], initProgressManagement: Int => ProgressManagement): BasicReport = {
    val t0 = System.nanoTime()
    val tplFiles = config.map(_.tplJars.map(new File(_))).getOrElse(Nil)

    // Get call graph (as returned by OPAL using selected algorithm)
    val callGraph = project.get(callGraphAlgorithm)

    val result = TPLMethodUsageAnalysis.analyze(
      project,
      callGraph,
      tplFiles,
      callGraphAlgorithmName,
      0.0 // analysis time will be set after measuring runtime below
    )
    val t1 = System.nanoTime()
    val elapsed = (t1 - t0) / 1e9

    // Update runtime in result
    val finalResult = result.copy(analysisTimeSeconds = BigDecimal(elapsed).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble)

    // Output results: to file (if set), or to console
    outputJsonFile match {
      case Some(path) =>
        JsonIO.writeResult(finalResult, path)
        BasicReport(s"Result written to $path\n")
      case None =>
        BasicReport(JsonIO.toJsonString(finalResult))
    }
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}