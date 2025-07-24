package main

import analyses.A_GodClassDetector.GodClassDetector
import analyses.B_CriticalMethodsDetector.CriticalMethodsDetector
import analyses.C_TPLUsageAnalyzer.TPLUsageAnalyzer
import analyses.D1_CriticalMethodsRemover.CriticalMethodsRemover
import analyses.D2_TPLMethodsRemover.TPLMethodsRemover
import analyses.E_DeadCodeDetector.DeadCodeDetector
import analyses.F_ArchitectureValidator.ArchitectureValidator
import analyses.SubAnalysis
import com.typesafe.scalalogging.Logger
import data.{SubAnalysisRun, Summary}
import org.opalj.log.{ConsoleOPALLogger, GlobalLogContext, OPALLogger}
import org.slf4j.MarkerFactory
import util.JsonIO.DEFAULT_INPUT_JSON_PATH
import util.{JsonIO, ProjectInitializer, Utils}

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable

object Main {

  private val HELP_TEXT =
    s"""==================== STATIC PROGRAM ANALYSIS SUITE ====================
       |-help:            Show this message and exit the program.
       |-config=<path>    Path to the config json for this program.
       |                  Default: $DEFAULT_INPUT_JSON_PATH in the current directory.
       |-initializeConfig Create a default json config $DEFAULT_INPUT_JSON_PATH
       |                  in the current directory and exit the program.
       |                  Cancels execution if $DEFAULT_INPUT_JSON_PATH already exists.
       |
       |The arguments are mutually exclusive - the program will not execute
       |if more than one argument is given.
       |
       |A json config is required to execute this program.
       |If -config is not provided and $DEFAULT_INPUT_JSON_PATH is not present
       |in the current directory, -initializeConfig is automatically executed.
       |Otherwise, if -config is not provided, the analysis suite gets executed
       |with the config in $DEFAULT_INPUT_JSON_PATH.
       |=======================================================================
       |""".stripMargin

  /**
   * Path where the input config json was read from.
   *
   * Exists for report in [[analyses.F_ArchitectureValidator.helpers.ArchitectureValidation]].
   */
  var inputJsonPath: String = DEFAULT_INPUT_JSON_PATH

  def main(args: Array[String]): Unit = {
    val programStartTime = System.currentTimeMillis()
    /* Checking arguments, possibly exit depending on argument(s) and current state */
    val jsonIO = new JsonIO

    // Avoid OPAL completely spamming into the console and therefore hiding the analysis log messages.
    // Only shows errors from OPAL.
    OPALLogger.updateLogger(GlobalLogContext, new ConsoleOPALLogger(ansiColored = false, minLogLevel = org.opalj.log.Error))

    // Don't allow more than one argument
    if (args.length > 1) {
      println(s"${Console.RED}ERROR: Expected at most one argument, received ${args.length}. ${Console.RESET}")
      println(HELP_TEXT)
      println("Exiting...")
      System.exit(1)
    }
    // If requested, print help text and exit
    if (args.contains("-help")) {
      println(HELP_TEXT)
      println("Exiting...")
      return
    }
    // If requested, initialize config
    if (args.contains("-initializeConfig")) {
      // Do not overwrite existing config to prevent mistakes
      if (new File(DEFAULT_INPUT_JSON_PATH).exists()) {
        println(s"${Console.RED}ERROR: Json file already exists at $DEFAULT_INPUT_JSON_PATH." +
          s"If you want to overwrite the file, you must delete the file before execution of this program. ${Console.RESET}")
        System.exit(1)
      }
      jsonIO.writeDefaultJson()
      println(s"${Console.BLUE}Wrote default json config at $DEFAULT_INPUT_JSON_PATH.${Console.RESET}")
      println("Exiting...")
      return
    }
    // No arguments given and default config not present
    if (args.isEmpty && !new File(DEFAULT_INPUT_JSON_PATH).exists()) {
      println(s"${Console.YELLOW}No arguments given and default json does not exist, execute -initializeConfig...${Console.RESET}")
      jsonIO.writeDefaultJson()
      println(s"${Console.BLUE}Wrote default json config at $DEFAULT_INPUT_JSON_PATH.${Console.RESET}")
      println("Exiting...")
      return
    }

    /* Begin setup of actual analysis */

    // Retrieve path where to read the json file from
    inputJsonPath = {
      val relevantArg = args.find(arg => arg.startsWith("-config="))
      if (relevantArg.isDefined) relevantArg.get.substring(8)
      else DEFAULT_INPUT_JSON_PATH
    }
    // Read json and retrieve output path
    val (outputPath, json) = jsonIO.readAnalysisConfigInit(inputJsonPath)
    System.setProperty("LOG_DIR", outputPath)

    // Create directory for the analysis results
    val outputDirectoryFile = new File(outputPath)
    // Catch edge case that a file already exists that isn't a directory
    if (outputDirectoryFile.isFile) {
      println(s"${Console.RED}ERROR: Output path \"$outputPath\" leads to file and not a directory.${Console.RESET}")
      println("Exiting...")
      System.exit(1)
    }
    if (!outputDirectoryFile.exists ) {
      try {
        Files.createDirectory(Path.of(outputPath))
        println(s"Created output directory $outputPath.")
      }
      catch {
        case _: java.io.IOException =>
          println(s"${Console.RED}ERROR: Could not create the directory \"$outputPath\" as parent directory does not exist.${Console.RESET}")
          println("Exiting...")
          System.exit(1)
      }
    }

    // Setup logger
    val logger = Logger("main")
    logger.info(s"Writing log to console and in file $outputPath/analysis.log.")
    logger.info(s"Reading config from $inputJsonPath...")

    // Retrieve config from json file
    val config = jsonIO.readStaticAnalysisConfig(json, outputPath)
    logger.info(s"Finished reading config.")

    // Print out project statistics to console
    logger.info(s"Retrieving statistics from OPAL project...")
    val sampleProject = ProjectInitializer.setupProject(logger, config.projectJars, config.libraryJars)
    logger.info(ProjectInitializer.projectStatistics(sampleProject))

    // Setup analyses
    val analyses: List[SubAnalysis] = List(
      new GodClassDetector(config.godClassDetector.execute),
      new CriticalMethodsDetector(config.criticalMethodsDetector.execute),
      new TPLUsageAnalyzer(config.tplUsageAnalyzer.execute),
      new CriticalMethodsRemover(config.criticalMethodsRemover.execute),
      new TPLMethodsRemover(config.tplMethodsRemover.execute),
      new DeadCodeDetector(config.deadCodeDetector.execute),
      new ArchitectureValidator(config.architectureValidator.execute)
    )

    val subAnalysisSummaries = mutable.ListBuffer[SubAnalysisRun]()

    analyses.foreach { subAnalysis =>
      if (subAnalysis.shouldExecute) {
        subAnalysis.logger.info(
          MarkerFactory.getMarker("BLUE"),
          s"Starting analysis ${subAnalysis.analysisNumber}: ${subAnalysis.analysisName}"
        )
        val outputPath = s"${config.resultsOutputPath}/${subAnalysis.outputFolderName}"
        val deletedFile = Utils.initializeSubAnalysisOutputDirectory(outputPath)
        logger.info(s"Initialized output path $outputPath.")
        if (deletedFile) logger.warn(s"Deleted at least one file during initialization.")

        val subAnalysisStartTime = System.currentTimeMillis()

        try {
          subAnalysis.executeAnalysis(config)
          val subAnalysisEndTime = System.currentTimeMillis()
          val runTime = (subAnalysisEndTime - subAnalysisStartTime) / 1000.0
          val duration = f"$runTime%.3f".replace(',', '.')
          subAnalysisSummaries += SubAnalysisRun(
            analysisName = subAnalysis.analysisName,
            successful = true,
            resultsPath = outputPath,
            timeFinished = java.time.LocalDateTime.now(),
            runTimeSeconds = runTime
          )
          subAnalysis.logger.info(
            MarkerFactory.getMarker("BLUE"),
            s"Finished analysis ${subAnalysis.analysisNumber} (${subAnalysis.analysisName}) in $duration seconds."
          )
        }
        catch {
          case e: Exception =>
            subAnalysis.logger.error(s"Analysis ${subAnalysis.analysisNumber} (${subAnalysis.analysisName}) terminated due to the following error:")
            subAnalysis.logger.error(s"--> ${e.toString}")
            val subAnalysisEndTime = System.currentTimeMillis()
            val runTime = (subAnalysisEndTime - subAnalysisStartTime) / 1000.0
            val duration = f"$runTime%.3f".replace(',', '.')
            subAnalysisSummaries += SubAnalysisRun(
              analysisName = subAnalysis.analysisName,
              successful = false,
              resultsPath = outputPath,
              timeFinished = java.time.LocalDateTime.now(),
              runTimeSeconds = runTime
            )
            subAnalysis.logger.error(
              s"Analysis ${subAnalysis.analysisNumber} (${subAnalysis.analysisName}) ran for $duration seconds before termination."
            )
        }
      }
      else {
        val outputPath = s"${config.resultsOutputPath}/${subAnalysis.outputFolderName}"
        val deletedFile = Utils.deleteSubAnalysisOutputDirectory(outputPath)
        if (deletedFile) logger.warn(s"Deleted $outputPath from a (probably) previous run.")
      }
    }
    val programFinishTime = System.currentTimeMillis()
    val timeFinished = java.time.LocalDateTime.now()
    val runTime = (programFinishTime - programStartTime) / 1000.0
    val duration = f"$runTime%.3f".replace(',', '.')
    val summary = Summary(
      totalRunTimeSeconds = runTime,
      timeFinished = timeFinished,
      analysesExecuted = subAnalysisSummaries.toList
    )
    jsonIO.writeSummary(summary, s"$outputPath/summary.json")
    logger.info("Analysis suite finished.")
    logger.info(s"Total run time: $duration seconds.")
    logger.info(s"Finished at ${timeFinished.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)}.")
  }
}

