import com.typesafe.config.{Config, ConfigFactory}
import create.{FileIO, TPLMethodUsageAnalysis}
import create.data.AnalysisConfig
import org.opalj.ba.toDA
import org.opalj.bc.Assembler
import org.opalj.br.analyses.{Analysis, AnalysisApplication, BasicReport, ProgressManagement, Project, ReportableAnalysisResult}
import org.opalj.log.LogContext
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, RTACallGraphKey, XTACallGraphKey}

import java.io.File
import java.net.URL
import java.nio.file.{Files, Paths}
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
  private var config: Option[AnalysisConfig] = None

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
    println("\nLoaded the following config:")
    println(s"* projectJars:")
    config.get.projectJars.foreach {file => println(s"  - $file")}
    println(s"* libraryJars:")
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
    println(s"* customEntryPoints:")
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
    println(s"* outputClassFiles: ${config.get.outputClassFiles}\n")

    // Create call graph and do analysis with it
    val callGraph = project.get(config.get.callGraphAlgorithm)
    val modifiedClassFiles = TPLMethodUsageAnalysis.analyzeAndCreate(project, callGraph, config.get)

    // Write created class files
    val outputPath = Paths.get(config.get.outputClassFiles)
    var replacedInvalidCharacter = false
    println(s"\nWriting created class files to path $outputPath ...\n")
    modifiedClassFiles.foreach { modifiedClassFile =>
      val usedMethods = modifiedClassFile.methods.length
      val sampleMethod = modifiedClassFile.methods(scala.util.Random.nextInt(usedMethods))
      println(s"Class file ${modifiedClassFile.fqn.replace('/','.')}:")
      println(s"  - Used Methods: $usedMethods")
      println(s"  - Sample method: ${sampleMethod.signatureToJava(true)}\n")

      val newClassBytes: Array[Byte] = Assembler(toDA(modifiedClassFile))

      // Windows does not accept some characters that may be contained in the class file names
      // Thus, replace them with similar-looking characters that are allowed
      val sanitizedClassFileName = modifiedClassFile.fqn.map { c =>
        c match {
          case ':' =>
            replacedInvalidCharacter = true
            'ː' // Unicode character U+02D0
          case '<' =>
            replacedInvalidCharacter = true
            '‹' // Unicode character U+2039
          case '>' =>
            replacedInvalidCharacter = true
            '›' // Unicode character U+203A
          case other => other
        }
      }

      val classFilePath = Paths.get(s"$outputPath/$sanitizedClassFileName.class")

      Files.createDirectories(classFilePath.getParent)
      Files.write(classFilePath, newClassBytes)
    }

    if (replacedInvalidCharacter) {
      println(s"${Console.BLUE}Note: At least one of the class files contained a character not allowed in Windows file names (':', '<' or '>').${Console.RESET}")
      println(s"${Console.BLUE}      Such characters have been replaced with similar-looking Unicode characters (U+02D0, U+2039 or U+203A).${Console.RESET}\n")
    }
    BasicReport("Finished writing class files, analysis executed successfully!\n")
  }

  override val analysis: Analysis[URL, ReportableAnalysisResult] = this
}
