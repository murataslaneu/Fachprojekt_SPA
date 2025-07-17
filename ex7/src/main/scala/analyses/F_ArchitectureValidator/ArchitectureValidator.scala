package analyses.F_ArchitectureValidator

import com.typesafe.config.Config
import data.{ArchitectureConfig, RecursiveWarnings}
import helpers.{ArchitectureJsonIO, ArchitectureValidation}
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.log.LogContext

import java.io.File
import java.net.URL
import scala.collection.mutable.ListBuffer

object ArchitectureValidator extends Analysis[URL, BasicReport] with AnalysisApplication {

  var configFile: Option[String] = None
  var specFile: Option[String] = None
  var outputPath: String = "architecture-report.json"
  var onlyMethodAndFieldAccesses: Boolean = false
  var config: Option[ArchitectureConfig] = None

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
      case arg if arg.equals("-onlyMethodAndFieldAccesses") => onlyMethodAndFieldAccesses = true
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
      | [-onlyMethodAndFieldAccesses (Only consider dependencies resulting from method and field accesses)]
      |
      | Either -config or -spec must be provided.
      | -config gets prioritized over all other options!
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
    val actualOnlyMethodAndFieldAccesses = config.map(_.onlyMethodAndFieldAccesses).getOrElse(onlyMethodAndFieldAccesses)

    println(s"\n==================== Architecture Validation ====================")
    println(s"* Specification file: $actualSpecFile")
    println(s"* Output file: $actualOutputPath")
    println(s"* Project JARs: ${project.allClassFiles.size} classes loaded")
    println(s"* ${if (actualOnlyMethodAndFieldAccesses) "Only considering dependencies resulting from method and field accesses"
    else "Considering all dependencies inside the project"}.")
    println("================================================================\n")

    println("Reading architecture specification...")
    val spec = ArchitectureJsonIO.readSpecification(actualSpecFile)

    println("Starting architecture validation...")

    val report = ArchitectureValidation.analyze(project, spec,
      config.getOrElse(ArchitectureConfig(List.empty, List.empty, actualSpecFile, actualOutputPath, actualOnlyMethodAndFieldAccesses)))

    println("Architecture validation finished.")
    println(s"Found ${report.violations.size} violations")
    println(s"Generated ${report.warningsCount} warnings")

    ArchitectureJsonIO.writeReport(report, actualOutputPath)
    println(s"Report written to $actualOutputPath")

    // Print summary
    if (report.violations.nonEmpty) {
      println("\nViolations found:")
      report.violations.take(5).foreach { violation =>
        println(s"  ${violation.fromPackage}.${violation.fromClass} -> ${violation.toPackage}.${violation.toClass} (${violation.accessType})")
      }
      if (report.violations.size > 5) {
        println(s"  ... and ${report.violations.size - 5} more violations")
      }
    }

    if (report.warnings.warnings.nonEmpty || report.warnings.innerWarnings.nonEmpty) {
      println("\nWarnings:")
      prettyPrintWarnings(report.warnings)
    }

    BasicReport("")
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this

  private def prettyPrintWarnings(warnings: RecursiveWarnings, indent: String = ""): Unit = {
    warnings.warnings.foreach { message => println(s"$indent- $message")}
    warnings.innerWarnings.foreach {case (rule, nestedWarnings) =>
      println(s"$indent$rule:")
      prettyPrintWarnings(nestedWarnings, indent + "  ")
    }
  }
}