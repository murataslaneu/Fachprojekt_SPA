package analyses.D2_TPLMethodsRemover

import com.typesafe.config.{Config, ConfigFactory}
import create.{FileIO, TPLMethodUsageAnalysis}
import create.data.AnalysisConfig
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.log.LogContext
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, RTACallGraphKey, XTACallGraphKey}

import java.io.File
import java.net.URL
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.{IterableHasAsJava, MapHasAsJava}


// Application that implements exercise 4.1.2

/**
 * Application that looks for all used third party library methods (TPLs, like in ex3). After doing that, it creates
 * new class files, only containing the used TPL methods, however without the method body.
 */
object TPLMethodsRemover extends Analysis[URL, BasicReport] with AnalysisApplication {

  /** Object holding the configuration for the analysis */
  var config: Option[AnalysisConfig] = None

  override def title: String = "Unused TPL methods remover"

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
          config = Some(FileIO.readJsonConfig(configPath))
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

    super.setupProject(config.get.projectJars, config.get.libraryJars, completelyLoadLibraries = true, configuredConfig = newConfig)
  }

  override def analyze(project: Project[URL], parameters: Seq[String], initProgressManagement: Int => ProgressManagement): BasicReport = {
    // Print config
    println("\n==================== Loaded Configuration ====================")
    println(s"* projectJars: ${if (config.get.projectJars.isEmpty) "[None]" else ""}")
    config.get.projectJars.foreach {file => println(s"  - $file")}
    println(s"* libraryJars: ${if (config.get.libraryJars.isEmpty) "[None]" else ""}")
    config.get.libraryJars.foreach {file => println(s"  - $file")}
    println(s"* tplJar: ${config.get.tplJar}")
    println(s"* includeNonPublicMethods: ${config.get.includeNonPublicMethods}")
    val entryPointsFinder = config.get.entryPointsFinder._1 match {
      case "org.opalj.br.analyses.cg.ConfigurationEntryPointsFinder" => "custom"
      case "org.opalj.br.analyses.cg.ApplicationWithoutJREEntryPointsFinder" => "application"
      case "org.opalj.br.analyses.cg.ApplicationEntryPointsFinder" => "applicationWithJre"
      case "org.opalj.br.analyses.cg.LibraryEntryPointsFinder" => "library"
    }
    println(s"* entryPointsFinder: $entryPointsFinder")
    println(s"* customEntryPoints: ${if (config.get.customEntryPoints.isEmpty) "[None]" else ""}")
    config.get.customEntryPoints.foreach { eps =>
      if (eps.methods.nonEmpty) {
        println(s"  - Class ${eps.className.replace('/', '.')}:")
        eps.methods.foreach { method => println(s"    -- $method")}
      }
    }
    val callGraphAlgorithmName = config.get.callGraphAlgorithm match {
      case CHACallGraphKey => "CHA"
      case RTACallGraphKey => "RTA"
      case XTACallGraphKey => "XTA"
      case CFA_1_1_CallGraphKey => "1-1-CFA"
    }
    println(s"* callGraphAlgorithm: $callGraphAlgorithmName")
    println(s"* outputClassFiles: ${config.get.outputClassFiles}")
    println("===============================================================\n")

    // Create call graph and do analysis with it
    println("Calculate call graph...")
    val callGraph = project.get(config.get.callGraphAlgorithm)
    println("Doing analysis on call graph...")
    val modifiedClassFiles = TPLMethodUsageAnalysis.analyzeAndCreate(project, callGraph, config.get)

    // Write created class files
    FileIO.writeModifiedClassFiles(config.get.outputClassFiles, modifiedClassFiles)
    BasicReport("Finished writing class files, analysis executed successfully!\n")
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}
