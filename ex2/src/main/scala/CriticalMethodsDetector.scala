import misc._
import analysis.CriticalMethodsAnalysis
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}


import java.net.URL
import scala.collection.mutable.ListBuffer



/**
 * Application that looks for possibly critical method calls in a software project regarding security.
 */
object CriticalMethodsDetector extends Analysis[URL, BasicReport] with AnalysisApplication {

  /** Holds the critical methods to look out for, grouped by class */
  private var criticalMethods: ListBuffer[CriticalClassMethods] = ListBuffer()

  /** Results string to print after analysis */
  private val analysisResults = new StringBuilder()

  override def title: String = "Critical methods detector"

  override def checkAnalysisSpecificParameters(parameters: Seq[String]): Iterable[String] = {
    /** Internal method to retrieve the value from the given parameter */
    def getValue(arg: String): String = arg.substring(arg.indexOf("=") + 1).strip()

    if (parameters.isEmpty) {
      return Nil
    }

    // Give the user the option to not look the SecurityManager methods if not wanted
    var ignoreSecurityManager = false
    val issues: ListBuffer[String] = ListBuffer()

    parameters.foreach { arg: String =>
      if (arg.startsWith("-include")) {
        val path = getValue(arg)

        if (!FileIO.fileReadable(path)) {
          issues += "-include: Could not read from file, must be a txt file."
        }
        else {
          criticalMethods = FileIO.readIncludeMethodsFile(path)
        }
      }
      else if (arg.equals("-ignoreSecurityManager")) ignoreSecurityManager = true
      else issues += s"unknown parameter: $arg"
    }

    if (!ignoreSecurityManager) {
      criticalMethods.addOne(
        CriticalClassMethods("java.lang.System", List("getSecurityManager", "setSecurityManager"))
      )
    }
    issues
  }

  override def analysisSpecificParametersDescription: String = super.analysisSpecificParametersDescription +
    s"""[-include=<FilePath> (Include a text file of methods that the detector should look out for as well. See the readme for more details)]
       |[-ignoreSecurityManager] (Flag that removes the methods System.getSecurityManger and setSecurityManager that are added by default)]""".stripMargin


  override def analyze(project: Project[URL], parameters: Seq[String], initProgressManagement: Int => ProgressManagement): BasicReport = {
    val results = CriticalMethodsAnalysis.analyze(project, criticalMethods.toList)
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
