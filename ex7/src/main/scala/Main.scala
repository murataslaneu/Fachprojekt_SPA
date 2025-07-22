import analyses.A_GodClassDetector.GodClassDetector
import analyses.B_CriticalMethodsDetector.CriticalMethodsDetector
import analyses.C_TPLUsageAnalyzer.TPLUsageAnalyzer
import analyses.D1_CriticalMethodsRemover.CriticalMethodsRemover
import analyses.D2_TPLMethodsRemover.TPLMethodsRemover
import analyses.SubAnalysis
import com.typesafe.scalalogging.Logger
import org.opalj.log.{ConsoleOPALLogger, GlobalLogContext, OPALLogger}
import org.slf4j.MarkerFactory
import util.JsonIO.DEFAULT_INPUT_JSON_PATH
import util.{JsonIO, ProjectInitializer}

import java.io.File
import java.nio.file.{Files, Path}

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

  def main(args: Array[String]): Unit = {
    /* Checking arguments, possibly exit depending on argument(s) and current state */
    val jsonIO = new JsonIO

    // Avoid OPAL completely spamming into the console and therefore hiding the analysis log messages.
    // Only show errors and warnings from OPAL.
    // Only reason warnings are also shown is that the user can see the program is still doing something
    // during the call graph generation.
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
    val inputJsonPath = {
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
    // TODO: Add missing analyses
    val analyses: List[SubAnalysis] = List(
      new GodClassDetector(config.godClassDetector.execute),
      new CriticalMethodsDetector(config.criticalMethodsDetector.execute),
      new TPLUsageAnalyzer(config.tplUsageAnalyzer.execute),
      new CriticalMethodsRemover(config.criticalMethodsRemover.execute),
      new TPLMethodsRemover(config.tplMethodsRemover.execute)
    )

    analyses.foreach { subAnalysis =>
      if (subAnalysis.shouldExecute) {
        subAnalysis.logger.info(
          MarkerFactory.getMarker("BLUE"),
          s"Starting analysis ${subAnalysis.analysisNumber}: ${subAnalysis.analysisName}"
        )
        val startTime = System.currentTimeMillis()

        try {
          subAnalysis.executeAnalysis(config)
          val endTime = System.currentTimeMillis()
          val duration = f"${(endTime - startTime) / 1000.0}%.3f".replace(',', '.')
          subAnalysis.logger.info(
            MarkerFactory.getMarker("BLUE"),
            s"Finished analysis ${subAnalysis.analysisNumber} (${subAnalysis.analysisName}) in $duration seconds."
          )
        }
        catch {
          case e: Exception =>
            subAnalysis.logger.error(s"Analysis ${subAnalysis.analysisNumber} (${subAnalysis.analysisName}) terminated due to the following error:")
            subAnalysis.logger.error(s"--> ${e.toString}")
            val endTime = System.currentTimeMillis()
            val duration = f"${(endTime - startTime) / 1000.0}%.3f".replace(',', '.')
            subAnalysis.logger.error(
              s"Analysis ${subAnalysis.analysisNumber} (${subAnalysis.analysisName}) ran for $duration seconds before termination."
            )
        }
      }
    }
  }
}

