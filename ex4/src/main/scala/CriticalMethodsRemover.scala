import com.typesafe.config.{Config, ConfigFactory}
import modify.JsonIO
import modify.data.AnalysisConfig
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.log.LogContext

import java.io.File
import java.net.URL
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.{IterableHasAsJava, MapHasAsJava}


// Application that implements exercise 4.1.1

/**
 * Application that searches for critical method calls (like in ex2) AND edits the bytecode to replaces these with
 * methods that return null. The program outputs new .class files where the edits are visible.
 */
object CriticalMethodsRemover extends Analysis[URL, BasicReport] with AnalysisApplication {

  /** Results string to print after analysis */
  private val analysisResults = new StringBuilder()

  /** Object holding the configuration for the analysis */
  private var config: Option[AnalysisConfig] = None

  /** Flag set during analysis to indicate if at least one found method call has been ignored. */
  private var ignoredAtLeastOneCall: Boolean = false

  override def title: String = "Critical methods remover"

  override def checkAnalysisSpecificParameters(parameters: Seq[String]): Iterable[String] = {
    /** Internal method to retrieve the value from the given parameter */
    def getValue(arg: String): String = arg.substring(arg.indexOf("=") + 1).strip()

    val issues: ListBuffer[String] = ListBuffer()
    var configMissing = true

    parameters.foreach {
      case arg if arg.startsWith("-config=") =>
        val configPath = getValue(arg)
        try {
          configMissing = false
          config = Some(JsonIO.readConfig(configPath))
        }
        catch {
          case ex: Exception => issues += s"Config file at path $configPath could not be parsed correctly: $ex"
        }
      case unknown => issues += s"Unknown parameter: $unknown"
    }

    if (config.isEmpty && configMissing) {
      issues += "-config: Missing. Please provide a (correctly formatted) config file with -config=config.json"
    }

    issues
  }

  override def analysisSpecificParametersDescription: String = """
      | ========================= CUSTOM PARAMETERS =========================
      | [-config=<config.json> (REQUIRED. Configuration used for analysis. See template for schema.)]
      |
      | This analysis uses a custom config json to configure the project.
      | OTHER OPTIONS BESIDES -help ARE IGNORED. PLEASE CONFIGURE PROJECT
      | AND LIBRARY JARS VIA THE CONFIG JSON.
      | """.stripMargin

  override def setupProject(cpFiles: Iterable[File], libcpFiles: Iterable[File], completelyLoadLibraries: Boolean, configuredConfig: Config)(implicit initialLogContext: LogContext): Project[URL] = {
    val overridesMap: mutable.Map[String, Object] = mutable.Map(
      "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis" -> config.get.entryPointsFinder._1,
      "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis" -> config.get.entryPointsFinder._2
    )

    if (config.get.customEntryPoints.nonEmpty) {
      val customEntryPoints = config.get.customEntryPoints.flatMap { eps =>
        eps.methods.map { epMethod =>
          Map("declaringClass" -> eps.className, "name" -> epMethod).asJava
        }
      }.asJava
      overridesMap.put("org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints", customEntryPoints)
    }

    val newConfig = ConfigFactory.parseMap(overridesMap.asJava).withFallback(configuredConfig).resolve()

    super.setupProject(config.get.projectJars, config.get.libraryJars, config.get.completelyLoadLibraries, newConfig)
  }

  // TODO
  override def analyze(project: Project[URL], parameters: Seq[String], initProgressManagement: Int => ProgressManagement): BasicReport = {
    // Printing out config
    // Mainly here just to check if loading the json config works as expected
    // Can either be kept (with better-looking output for the user), or just removed.
    println("Loaded the following config:")
    println(s"  - projectJars: ${config.get.projectJars}")
    println(s"  - libraryJars: ${config.get.libraryJars}")
    println(s"  - completelyLoadLibraries: ${config.get.completelyLoadLibraries}")
    println(s"  - criticalMethods: ${config.get.criticalMethods}")
    println(s"  - ignoreCalls: ${config.get.ignoreCalls}")
    println(s"  - entryPointsFinder: ${config.get.entryPointsFinder}")
    println(s"  - customEntryPoints: ${config.get.customEntryPoints}")
    println(s"  - callGraphAlgorithm: ${config.get.callGraphAlgorithm}")
    println(s"  - outputJson: ${config.get.outputJson}")


    BasicReport(analysisResults.toString)
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}
