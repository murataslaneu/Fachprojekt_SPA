import misc._
import analysis.CriticalMethodsAnalysis
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.tac.cg.{CHACallGraphKey, CTACallGraphKey, CallGraphKey, RTACallGraphKey, XTACallGraphKey}

import java.net.URL
import scala.collection.mutable.ListBuffer


/**
 * Application that looks for possibly critical method calls in a software project regarding security.
 */
object CriticalMethodsDetector extends Analysis[URL, BasicReport] with AnalysisApplication {

  /** Results string to print after analysis */
  private val analysisResults = new StringBuilder()

  /** Selected algorithm to use. Default: RTA */
  private var callGraphAlgorithm: CallGraphKey = RTACallGraphKey

  /** Holds the critical methods to look out for, grouped by class */
  private var criticalMethods: ListBuffer[CriticalClassMethods] = ListBuffer(CriticalClassMethods(
    "java.lang.System",
    List("getSecurityManager", "setSecurityManager")
  ))

  /** Used to suppress warnings during analysis */
  private var suppressedCalls: ListBuffer[SuppressedCall] = ListBuffer()

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
      else if (arg.startsWith("-suppress=")) {
        val path = getValue(arg)

        if (!FileIO.fileReadable(path)) {
          issues += "-suppress: Could not read from file, must be a txt file."
        }
        else {
          suppressedCalls = FileIO.readSuppressCallsFile(path)
        }
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
    issues
  }

  override def analysisSpecificParametersDescription: String = super.analysisSpecificParametersDescription +
    s"""[-include=<FilePath> (Include a text file of methods that the detector should look out for as well. See the readme for more details)]
       |[-suppress=<FilePath> (Include a text file of specific method calls that should be suppressed from warnings)]
       |[-alg=<algorithm> (The algorithm to generate a call graph. Available are: CHA, RTA, XTA, CTA)]""".stripMargin

  override def analyze(project: Project[URL], parameters: Seq[String], initProgressManagement: Int => ProgressManagement): BasicReport = {
    val callGraph = project.get(callGraphAlgorithm)
    val results = CriticalMethodsAnalysis.analyze(callGraph, criticalMethods.toList, suppressedCalls.toList)
    analysisResults.append("# ------------------- Analysis Results ------------------- #\n\n")

    if (results.nonEmpty) {
      results.foreach { result =>
        analysisResults.append(result + "\n")
      }
    } else {
      analysisResults.append("No warnings here :)\n")
    }

    BasicReport(analysisResults.toString)
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}
