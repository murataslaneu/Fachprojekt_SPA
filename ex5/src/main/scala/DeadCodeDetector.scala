import com.typesafe.config.Config
import data.AnalysisConfig
import helpers.{DeadCodeAnalysis, JsonIO}
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.log.LogContext

import java.io.File
import java.net.URL
import scala.collection.mutable.ListBuffer

// Application that does the analysis part

/**
 * Application that searches through the project for dead instructions
 * (i.e. instructions that will under no circumstances be executed during runtime)
 */
object DeadCodeDetector extends Analysis[URL, BasicReport] with AnalysisApplication {

  /** Boolean whether the -interactive flag has been entered in the terminal */
  private var interactive: Boolean = false

  /** Boolean whether the -showResults flag has been entered in the terminal */
  private var showResults: Boolean = false

  /** String entered in the terminal where the result json file should be written to */
  private var outputJsonPath: String = "result.json"

  /** Boolean whether the -config option has been entered in the terminal */
  var configSet: Boolean = false

  /** Object holding the configuration for the analysis */
  var config: Option[AnalysisConfig] = None

  override def title: String = "Dead code detector"

  override def checkAnalysisSpecificParameters(parameters: Seq[String]): Iterable[String] = {
    /** Internal method to retrieve the value from the given parameter */
    def getValue(arg: String): String = arg.substring(arg.indexOf("=") + 1).strip()

    val issues: ListBuffer[String] = ListBuffer()

    parameters.foreach {
      case arg if arg.startsWith("-config=") =>
        val configPath = getValue(arg)
        try {
          configSet = true
          config = Some(JsonIO.readJsonConfig(configPath))
        }
        catch {
          case ex: Exception => issues += s"Config file at path $configPath could not be parsed correctly: $ex"
        }
      case arg if arg.equals("-interactive") => interactive = true
      case arg if arg.equals("-showResults") => showResults = true
      case arg if arg.startsWith("-outputJson=") => outputJsonPath = getValue(arg)
      case unknown => issues += s"Unknown parameter: $unknown"
    }

    if (configSet) {
      println(s"${Console.BLUE}Config loaded from json file. NOTE: The config is prioritized over all other options! " +
        s"If you entered another parameter in the terminal, it will be ignored!${Console.RESET}")
    }

    issues
  }

  override def analysisSpecificParametersDescription: String =
    """
      | ========================= CUSTOM PARAMETERS =========================
      | [-config=<config.json> (Optional. Configuration used for analysis. See template for schema.)]
      | [-interactive (Flag. If given, the analysis will ask you what domain to use for the abstract interpretation.)]
      | [-showResults (Flag. If given, the DeadCodeReportViewer will be automatically started after the analysis finishes.)]
      | [-outputJson=<result.json> (Optional string. Output path for the generated json file containing the analysis results.)]
      |
      | Note: This analysis can be configured with a custom config json instead of via the terminal.
      | IF GIVEN, ALL OTHER OPTIONS BESIDES -help ARE IGNORED.
      | """.stripMargin

  override def setupProject(cpFiles: Iterable[File], libcpFiles: Iterable[File], completelyLoadLibraries: Boolean, configuredConfig: Config)(implicit initialLogContext: LogContext): Project[URL] = {
    if (configSet) {
      super.setupProject(config.get.projectJars, config.get.libraryJars, config.get.completelyLoadLibraries, configuredConfig)
    }
    else {
      this.config = Some(AnalysisConfig(cpFiles.toList, libcpFiles.toList, completelyLoadLibraries, interactive, showResults, outputJsonPath))
      super.setupProject(cpFiles, libcpFiles, completelyLoadLibraries, configuredConfig)
    }
  }

  override def analyze(project: Project[URL], parameters: Seq[String], initProgressManagement: Int => ProgressManagement): BasicReport = {
    // Print config
    println(s"\n==================== Loaded Configuration (${if (configSet) "via config json" else "via terminal options"}) ====================")
    println(s"* projectJars: ${if (config.get.projectJars.isEmpty) "[None]" else ""}")
    config.get.projectJars.foreach { file => println(s"  - $file") }
    println(s"* libraryJars: ${if (config.get.libraryJars.isEmpty) "[None]" else ""}")
    config.get.libraryJars.foreach { file => println(s"  - $file") }
    println(s"* completelyLoadLibraries: ${config.get.completelyLoadLibraries}")
    println(s"* interactive: ${config.get.interactive}")
    println(s"* showResults: ${config.get.showResults}")
    println(s"* outputJson path: ${config.get.outputJson}")
    println("===============================================================\n")

    println("Selecting domain...")
    var domainStr = DeadCodeAnalysis.selectDomain(config.get.interactive)
    // This is needed because there is likely a bug in the way OPAL 5.0.0 handles domain identifiers.
    val domainName = domainStr.substring(domainStr.indexOf("[") + 1, domainStr.indexOf("]"))
    domainStr = domainStr.substring(domainStr.indexOf("]") + 2)

    println("Starting analysis...")
    val report = DeadCodeAnalysis.analyze(project, domainStr, domainName, config.get)
    println("Analysis finished.")

    JsonIO.writeResult(report, config.get.outputJson)
    println(s"Result written to ${config.get.outputJson}.")

    BasicReport("")
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}
