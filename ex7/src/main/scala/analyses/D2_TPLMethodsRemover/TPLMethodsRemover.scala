package analyses.D2_TPLMethodsRemover

import analyses.SubAnalysis
import com.typesafe.scalalogging.Logger
import configs.{StaticAnalysisConfig, TPLMethodsRemoverConfig}
import create.{FileIO, TPLMethodUsageAnalysis}
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, CTACallGraphKey, RTACallGraphKey, XTACallGraphKey}
import org.slf4j.MarkerFactory
import util.{ProjectInitializer, Utils}

import java.io.{File, FileNotFoundException}
import scala.util.Random


/**
 * Application that looks for all used third party library methods (TPLs, like in ex3). After doing that, it creates
 * new class files, only containing the used TPL methods, however without the method body.
 */
class TPLMethodsRemover(override val shouldExecute: Boolean) extends SubAnalysis {

  /** Logger used inside this sub-analysis */
  override val logger: Logger = Logger("TPLMethodsRemover")
  /** The name of the sub-analysis */
  override val analysisName: String = "Unused TPL methods remover"
  /** The number of the sub-analysis */
  override val analysisNumber: String = "4b"

  override def executeAnalysis(config: StaticAnalysisConfig): Unit = {
    // Perform checks (and maybe modify) config
    val analysisConfig = checkTplJar(config)

    val callGraphAlgorithmName = analysisConfig.callGraphAlgorithmName.toUpperCase

    // Print out configuration
    val entryPointsFinder = analysisConfig.entryPointsFinder match {
      case "custom" => "Only custom entry points"
      case "application" => "Application (without JRE)"
      case "applicationwithjre" => "Application with JRE"
      case "library" => "Library"
      case invalid => throw new IllegalArgumentException(s"Invalid entry points finder $invalid selected.")
    }
    val customEntryPointsString = Utils.buildSampleSelectedMethodsString(analysisConfig.customEntryPoints, 5, 3)
    logger.info(
      s"""Configuration:
         |  - TPL jar to recreate: ${analysisConfig.tplJar}
         |  - Include non-public methods: ${analysisConfig.includeNonPublicMethods}
         |  - Call graph algorithm: $callGraphAlgorithmName
         |  - Entry points finder: $entryPointsFinder
         |  - Custom entry points: $customEntryPointsString""".stripMargin
    )

    // Set up project
    logger.info("Initializing OPAL project...")
    val opalConfig = ProjectInitializer.setupOPALProjectConfig(analysisConfig.entryPointsFinder, analysisConfig.customEntryPoints)
    val project = ProjectInitializer.setupProject(
      logger = logger,
      cpFiles = config.projectJars,
      libcpFiles = config.libraryJars,
      completelyLoadLibraries = true,
      configuredConfig = opalConfig
    )
    val callGraphKey = callGraphAlgorithmName match {
      case "CHA" => CHACallGraphKey
      case "RTA" => RTACallGraphKey
      case "XTA" => XTACallGraphKey
      case "CTA" => CTACallGraphKey
      case "1-1-CFA" => CFA_1_1_CallGraphKey
    }
    logger.info("Project initialization finished. Starting analysis on project...")

    // Get call graph (as returned by OPAL using selected algorithm)
    logger.info(
      MarkerFactory.getMarker("BLUE"),
      s"Beginning calculation of the $callGraphAlgorithmName call graph..."
    )
    logger.info(
      MarkerFactory.getMarker("BLUE"),
      s"(This might take a while, depending on the call graph algorithm and size of the project and libraries!)"
    )
    val callGraph = project.get(callGraphKey)
    logger.info(s"Finished calculation of the $callGraphAlgorithmName call graph.")

    logger.info("Beginning analysis on the call graph...")
    val modifiedClassFiles = TPLMethodUsageAnalysis.analyzeAndCreate(project, callGraph, analysisConfig)

    // Write created class files
    val outputDir = s"${config.resultsOutputPath}/4b_TPLMethodsRemover"
    val classesOutputDir = s"$outputDir/tplDummy"
    FileIO.writeModifiedClassFiles(logger, classesOutputDir, modifiedClassFiles)
    logger.info("Finished writing class files, TPL dummy created successfully.")
    val jsonOutputPath = s"$outputDir/report.json"
    FileIO.writeJsonReport(
      modifiedClassFiles.toList,
      config = config,
      tplJar = analysisConfig.tplJar,
      dummyPath = classesOutputDir,
      outputPath = jsonOutputPath
    )
    logger.info(s"Wrote json report to $jsonOutputPath.")
  }

  /**
   * Performs checks on the tplJar given via the config and maybe throws exceptions if conditions are not met.
   *
   * If the value is "DEFAULT", a random library jar given from libraryJars. In this case, a modified
   * TPLMethodsRemoverConfig is returned (with tplJar replaced by the randomly selected library)
   *
   * @param config The StaticAnalysisConfig given.
   * @return The TPLMethodsRemoverConfig that can be used for this analysis.
   */
  private def checkTplJar(config: StaticAnalysisConfig): TPLMethodsRemoverConfig = {
    val analysisConfig = config.tplMethodsRemover
    // Checks/Initializations regarding tplJar
    if (analysisConfig.tplJar == "DEFAULT") {
      // If "DEFAULT", select random library jar to create dummy from
      logger.info("Selected \"DEFAULT\" for \"tplJar\". Choosing a random library...")
      if (config.libraryJars.isEmpty) {
        logger.error("No library jars given in \"libraryJars\"! No TPL to create dummy from, terminating...")
        throw new IllegalArgumentException("No TPL jar available to create dummy from.")
      }
      val tplJar = config.libraryJars(Random.nextInt(config.libraryJars.length)).getPath.replace('\\','/')
      logger.info(s"Chose $tplJar randomly.")
      analysisConfig.copy(tplJar = tplJar)
    }
    else {
      // Check if tplJar leads to a jar specified in config.libraryJars
      val tplFile = new File(analysisConfig.tplJar)
      if (!tplFile.exists) {
        logger.error(s"Given tplPath ${analysisConfig.tplJar} is invalid! Terminating sub-analysis...")
        throw new FileNotFoundException(
          "Given TPL path is invalid. The path must lead to a library given via \"libraryJars\"!"
        )
      }
      val libraryTplFile = config.libraryJars.collectFirst { case libFile if libFile.getPath == tplFile.getPath ||
        libFile.getAbsolutePath == tplFile.getAbsolutePath => libFile
      }
      if (libraryTplFile.isEmpty) {
        logger.error("Given tplPath does not lead to a path given via \"libraryJars\"! Terminating sub-analysis...")
        logger.error("Tip: Copy the path of the corresponding jar from \"libraryJars\" to avoid this error.")
        throw new IllegalArgumentException(
          "Given path does not lead to a path given via \"libraryJars!\""
        )
      }
      analysisConfig
    }
  }
}
