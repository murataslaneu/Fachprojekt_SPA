import com.typesafe.scalalogging.Logger
import util.JsonIO.DEFAULT_INPUT_JSON_PATH
import util.JsonIO

import java.io.File

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
        return
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

    // Setup logger
    val logger = Logger("main")
    logger.info(s"Writing log to console and in file $outputPath/analysis.log")
    logger.info(s"Reading config from $inputJsonPath...")

    // Retrieve config from json file
    val config = jsonIO.readStaticAnalysisConfig(json, outputPath)
    logger.info(s"Finished reading config.")
  }
}

