import com.typesafe.config.Config
import data.ArchitectureConfig
import helpers.{ArchitectureJsonIO, ArchitectureValidation}
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.log.LogContext

import java.io.File
import java.net.URL
import scala.collection.mutable.ListBuffer

object ArchitectureValidator extends Analysis[URL, BasicReport] with AnalysisApplication {

  private var configFile: Option[String] = None
  private var specFile: Option[String] = None
  private var outputPath: String = "architecture-report.json"
  private var config: Option[ArchitectureConfig] = None

  override def title: String = "Architecture Validator"

  override def checkAnalysisSpecificParameters(parameters: Seq[String]): Iterable[String] = {
    def getValue(arg: String): String = arg.substring(arg.indexOf("=") + 1).strip()

    val issues: ListBuffer[String] = ListBuffer()

    parameters.foreach {
      case arg if arg.startsWith("-config=") =>
        val configPath = getValue(arg)
        try {
          config = Some(ArchitectureJsonIO.readConfig(configPath))
          configFile = Some(configPath)
        } catch {
          case ex: Exception => issues += s"Config file at path $configPath could not be parsed: $ex"
        }
      case arg if arg.startsWith("-spec=") =>
        specFile = Some(getValue(arg))
      case arg if arg.startsWith("-output=") =>
        outputPath = getValue(arg)
      case unknown => issues += s"Unknown parameter: $unknown"
    }

    if (config.isEmpty && specFile.isEmpty) {
      issues += "Either -config=<file> or -spec=<file> must be provided"
    }

    issues
  }

  override def analysisSpecificParametersDescription: String =
    """
      | ========================= ARCHITECTURE VALIDATOR =========================
      | [-config=<config.json> (Configuration file containing all analysis parameters)]
      | [-spec=<spec.json> (Architecture specification file)]
      | [-output=<output.json> (Output file for the analysis report)]
      |
      | Either -config or -spec must be provided.
      | """.stripMargin

  override def setupProject(cpFiles: Iterable[File], libcpFiles: Iterable[File],
                            completelyLoadLibraries: Boolean, configuredConfig: Config)
                           (implicit initialLogContext: LogContext): Project[URL] = {
    config match {
      case Some(cfg) =>
        super.setupProject(cfg.projectJars, cfg.libraryJars, cfg.completelyLoadLibraries, configuredConfig)
      case None =>
        super.setupProject(cpFiles, libcpFiles, completelyLoadLibraries, configuredConfig)
    }
  }

  override def analyze(project: Project[URL], parameters: Seq[String],
                       initProgressManagement: Int => ProgressManagement): BasicReport = {

    val actualSpecFile = config.map(_.specificationFile).orElse(specFile).get
    val actualOutputPath = config.map(_.outputJson).getOrElse(outputPath)

    println(s"\n==================== Architecture Validation ====================")
    println(s"* Specification file: $actualSpecFile")
    println(s"* Output file: $actualOutputPath")
    println(s"* Project JARs: ${project.allClassFiles.size} classes loaded")
    println("================================================================\n")

    println("Starting architecture validation...")

    val report = ArchitectureValidation.analyze(project, actualSpecFile,
      config.getOrElse(ArchitectureConfig(List.empty, List.empty, actualSpecFile, actualOutputPath)))

    println("Architecture validation finished.")
    println(s"Found ${report.violations.size} violations")
    println(s"Generated ${report.warnings.size} warnings")

    ArchitectureJsonIO.writeReport(report, actualOutputPath)
    println(s"Report written to $actualOutputPath")

    // Print summary
    if (report.violations.nonEmpty) {
      println("\nViolations found:")
      report.violations.take(5).foreach { violation =>
        println(s"  ${violation.fromClass} -> ${violation.toClass} (${violation.accessType})")
      }
      if (report.violations.size > 5) {
        println(s"  ... and ${report.violations.size - 5} more violations")
      }
    }

    if (report.warnings.nonEmpty) {
      println("\nWarnings:")
      report.warnings.foreach(println)
    }

    BasicReport("")
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}