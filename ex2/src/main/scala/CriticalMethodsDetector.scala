import misc._
import analysis.CriticalMethodsAnalysis
import com.typesafe.config.{Config, ConfigFactory}
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.log.LogContext
import org.opalj.tac.cg.{CHACallGraphKey, CTACallGraphKey, CallGraphKey, RTACallGraphKey, XTACallGraphKey}

import java.io.File
import java.net.URL
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.{IterableHasAsJava, MapHasAsJava}


/**
 * Application that looks for possibly critical method calls in a software project regarding security.
 */
object CriticalMethodsDetector extends Analysis[URL, BasicReport] with AnalysisApplication {

  /** Results string to print after analysis */
  private val analysisResults = new StringBuilder()

  /**
   * Flag whether the application entry points (*including* JRE main methods) should be included in the call graph.
   *
   * This flag is mutually exclusive to the [[includeApplicationWithoutJREEntryPoints]]
   */
  private var includeApplicationEntryPoints: Boolean = false

  /**
   * Flag whether the application entry points (*excluding* JRE main methods) should be included in the call graph.
   *
   * This flag is mutually exclusive to the [[includeApplicationEntryPoints]]
   */
  private var includeApplicationWithoutJREEntryPoints: Boolean = false

  /** Selected algorithm to use. Default: RTA */
  private var callGraphAlgorithm: CallGraphKey = RTACallGraphKey

  /** Holds the critical methods to look out for, grouped by class */
  private var criticalMethods: ListBuffer[SelectedMethodsOfClass] = ListBuffer(SelectedMethodsOfClass(
    "java.lang.System",
    List("getSecurityManager", "setSecurityManager")
  ))

  /** Holds the entry points if set by the user. Otherwise, the normal ApplicationWithoutJREEntryPointsFinder is used */
  private var customEntryPoints: ListBuffer[SelectedMethodsOfClass] = ListBuffer()

  /** Used to suppress warnings during analysis */
  private var suppressedCalls: ListBuffer[SuppressedCall] = ListBuffer()

  /** Flag set during analysis to indicate if at least one found method call has been suppressed. */
  private var suppressedAtLeastOneCall: Boolean = false

  override def title: String = "Critical methods detector"

  override def checkAnalysisSpecificParameters(parameters: Seq[String]): Iterable[String] = {
    /** Internal method to retrieve the value from the given parameter */
    def getValue(arg: String): String = arg.substring(arg.indexOf("=") + 1).strip()

    if (parameters.isEmpty) {
      return Nil
    }

    val issues: ListBuffer[String] = ListBuffer()

    parameters.foreach { arg: String =>
      if (arg.startsWith("-include=")) {
        val path = getValue(arg)

        if (!FileIO.fileReadable(path)) {
          issues += "-include: Could not read from file, must be a txt file."
        }
        else {
          criticalMethods = FileIO.readIncludeMethodsFile(path)
        }
      }
      else if (arg.startsWith("-entryPoints=")) {
        val path = getValue(arg)
        if (!FileIO.fileReadable(path)) {
          issues += "-entryPoints: Could not read from file, must be a txt file."
        }
        else {
          customEntryPoints = FileIO.readEntryPointsFile(path)
        }
      }
      else if (arg.startsWith("-suppress=")) {
        val path = getValue(arg)

        if (!FileIO.fileReadable(path)) {
          issues += "-suppress: Could not read from file, must be a txt file."
        }
        else {
          suppressedCalls = FileIO.readSuppressCallsFile(path)
        }
      }
      else if (arg.equals("-includeApplicationEntries")) {
        includeApplicationWithoutJREEntryPoints = true
      }
      else if (arg.equals("-includeApplicationWithJREEntries")) {
        includeApplicationEntryPoints = true
      }
      else if (arg.startsWith("-alg=")) {
        val alg = getValue(arg).toLowerCase()
        callGraphAlgorithm = (alg match {
          case "cha" => CHACallGraphKey
          case "rta" => RTACallGraphKey
          case "xta" => XTACallGraphKey
          case "cta" => CTACallGraphKey
          case _ =>
            issues += s"-alg: Unknown algorithm $alg. Available are: CHA, RTA, XTA, CTA"
            CHACallGraphKey
        }): CallGraphKey
      }
      else issues += s"unknown parameter: $arg"
    }

    if (includeApplicationEntryPoints && includeApplicationWithoutJREEntryPoints) {
      issues += "-includeApplicationEntryPoints and -includeApplicationWithJREEntries are mutually exclusive. " +
        "Please select at most one of the options."
    }
    else if (!includeApplicationEntryPoints && !includeApplicationWithoutJREEntryPoints && customEntryPoints.isEmpty) {
      // If no configuration for the entry points if given whatsoever, default to application entry points without JRE
      // (also OPALs default configuration)
      includeApplicationWithoutJREEntryPoints = true
    }

    issues
  }

  override def analysisSpecificParametersDescription: String = super.analysisSpecificParametersDescription +
    s"""[-include=<FilePath> (Include a text file of methods that the detector should look out for as well. See the readme for more details)]
       |[-suppress=<FilePath> (Include a text file of specific method calls that should be suppressed from warnings)]
       |[-entryPoints=<FilePath> (Include a text file of methods that the detector uses as entry points of the program.)]
       |[-includeApplicationEntries (Flag whether to include the main entry points in the analysis. Default: True if entryPoints option not given, false if entryPoints given)]
       |[-includeApplicationWithJREEntries (Flag whether to include the main entry points in the analysis, including those from the JRE. Default: False. Mutually exclusive to -includeApplicationEntries.]
       |[-alg=<algorithm> (The algorithm to generate a call graph. Available are: CHA, RTA, XTA, CTA)]""".stripMargin

  override def setupProject(cpFiles: Iterable[File], libcpFiles: Iterable[File], completelyLoadLibraries: Boolean, configuredConfig: Config)(implicit initialLogContext: LogContext): Project[URL] = {
    var mainEntryPoints = "org.opalj.br.analyses.cg.ConfigurationEntryPointsFinder"
    if (includeApplicationWithoutJREEntryPoints) {
      mainEntryPoints = "org.opalj.br.analyses.cg.ApplicationWithoutJREEntryPointsFinder"
    }
    else if (includeApplicationEntryPoints) {
      mainEntryPoints = "org.opalj.br.analyses.cg.ApplicationEntryPointsFinder"
    }

    val additionalEntryPoints = customEntryPoints.flatMap { eps =>
      eps.selectedMethods.map { epMethod =>
        Map("declaringClass" -> eps.className, "name" -> epMethod).asJava
      }
    }.asJava

    val overrides = ConfigFactory.parseMap(Map(
      "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis" -> mainEntryPoints,
      "org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints" -> additionalEntryPoints
    ).asJava)
    val newConfig = overrides.withFallback(configuredConfig).resolve()
    super.setupProject(cpFiles, libcpFiles, completelyLoadLibraries, newConfig)
  }

  override def analyze(project: Project[URL], parameters: Seq[String], initProgressManagement: Int => ProgressManagement): BasicReport = {
    val callGraph = project.get(callGraphAlgorithm)
    val tuple = CriticalMethodsAnalysis.analyze(callGraph, criticalMethods.toList, suppressedCalls.toList)
    val results = tuple._1
    suppressedAtLeastOneCall = tuple._2
    analysisResults.append("# ------------------- Analysis Results ------------------- #\n\n")

    if (results.nonEmpty) {
      results.foreach { result =>
        analysisResults.append(result + "\n")
      }
      if (suppressedAtLeastOneCall) analysisResults.append("Other method calls have been found but were suppressed.\n")
    } else {
      analysisResults.append("No warnings :)\n")
      if (suppressedAtLeastOneCall) analysisResults.append("Method calls have been found but were suppressed.\n")
    }

    BasicReport(analysisResults.toString)
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}
