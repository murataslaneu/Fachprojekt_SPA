package util

import analyses.F_ArchitectureValidator.data.ArchitectureSpec.ruleFormat
import analyses.F_ArchitectureValidator.data.Rule
import com.typesafe.scalalogging.Logger
import configs.{ArchitectureValidatorConfig, CriticalMethodsDetectorConfig, CriticalMethodsRemoverConfig, DeadCodeDetectorConfig, GodClassDetectorConfig, StaticAnalysisConfig, TPLMethodsRemoverConfig, TPLUsageAnalyzerConfig}
import data.{IgnoredCall, SelectedMethodsOfClass, Summary}
import play.api.libs.json.{JsDefined, JsError, JsString, JsSuccess, JsUndefined, JsValue, Json, Reads}

import java.io.{File, PrintWriter}

class JsonIO {
  /**
   * Logger used to log what is happening while reading the analysis.
   *
   * Gets initialized in [[readStaticAnalysisConfig]].
   */
  private var logger: Logger = _

  def writeDefaultJson(): Unit = {
    // Create default json
    val json: JsValue = Json.obj(
      "projectJars" -> Json.arr(), // TODO: Maybe add analysis application itself as default
      "libraryJars" -> Json.arr(), // TODO: Maybe add dependencies of analysis application itself as default
      "resultsOutputPath" -> JsonIO.DEFAULT_OUTPUT_DIRECTORY,
      "godClassDetector" -> Json.obj(
        "execute" -> false,
        "wmcThresh" -> "DEFAULT",
        "tccThresh" -> "DEFAULT",
        "atfdThresh" -> "DEFAULT",
        "nofThresh" -> "DEFAULT"
      ),
      "criticalMethodsDetector" -> Json.obj(
        "execute" -> false,
        "criticalMethods" -> "DEFAULT",
        "ignore" -> "DEFAULT",
        "callGraphAlgorithmName" -> "DEFAULT",
        "entryPointsFinder" -> "DEFAULT",
        "customEntryPoints" -> "DEFAULT"
      ),
      "tplUsageAnalyzer" -> Json.obj(
        "execute" -> false,
        "countAllMethods" -> "DEFAULT",
        "callGraphAlgorithmName" -> "DEFAULT",
        "entryPointsFinder" -> "DEFAULT",
        "customEntryPoints" -> "DEFAULT"
      ),
      "criticalMethodsRemover" -> Json.obj(
        "execute" -> false,
        "criticalMethods" -> "DEFAULT",
        "ignore" -> "DEFAULT"
      ),
      "tplMethodsRemover" -> Json.obj(
        "execute" -> false,
        "tplJar" -> "DEFAULT",
        "includeNonPublicMethods" -> "DEFAULT",
        "callGraphAlgorithmName" -> "DEFAULT",
        "entryPointsFinder" -> "DEFAULT",
        "customEntryPoints" -> "DEFAULT"
      ),
      "deadCodeDetector" -> Json.obj(
        "execute" -> false,
        "completelyLoadLibraries" -> "DEFAULT"
      ),
      "architectureValidator" -> Json.obj(
        "execute" -> false,
        "onlyMethodAndFieldAccesses" -> "DEFAULT",
        "defaultRule" -> "DEFAULT",
        "rules" -> "DEFAULT"
      )
    )

    // Write default json
    val writer = new PrintWriter(new File(JsonIO.DEFAULT_INPUT_JSON_PATH))
    writer.write(Json.prettyPrint(json))
    writer.close()
  }

  /**
   * Reads the json file at the given path and also retrieves the output for the analysis.
   *
   * Output path needed due to the logger.
   * */
  def readAnalysisConfigInit(configPath: String): (String, JsValue) = {
    val source = scala.io.Source.fromFile(configPath)
    val json = try Json.parse(source.mkString) finally source.close()

    val outputPath = {
      val result = json \ "resultsOutputPath"
      if (result.isEmpty) throw new NoSuchElementException(
        "Json config missing parameter \"resultsOutputPath\"."
      )
      val path = result.get.as[String]
      if (path == "DEFAULT") JsonIO.DEFAULT_OUTPUT_DIRECTORY
      else path
    }.replace('\\', '/')

    (outputPath, json)
  }

  /**
   * Finalizes reading the json file, with logging of warnings and errors.
   */
  def readStaticAnalysisConfig(json: JsValue, outputPath: String): StaticAnalysisConfig = {
    logger = Logger("config")

    /* Reading base analysis config */
    val projectJarFiles = readProjectJarFiles(json)
    val libraryJarFiles = readLibraryJarFiles(json)
    // resultsOutputPath already read

    /* Reading configs for each analysis */
    val godClassDetectorConfig = readGodClassDetectorConfig(json)
    val criticalMethodsDetectorConfig = readCriticalMethodsDetectorConfig(json)
    val tplUsageAnalyzerConfig = readTPLUsageAnalyzerConfig(json, criticalMethodsDetectorConfig)
    val criticalMethodsRemoverConfig = readCriticalMethodsRemoverConfig(json, criticalMethodsDetectorConfig)
    val tplMethodsRemoverConfig = readTPLMethodsRemoverConfig(json, tplUsageAnalyzerConfig)
    val deadCodeDetectorConfig = readDeadCodeDetectorConfig(json)
    val architectureValidatorConfig = readArchitectureValidatorConfig(json)

    StaticAnalysisConfig(
      projectJarFiles,
      libraryJarFiles,
      outputPath,
      godClassDetectorConfig,
      criticalMethodsDetectorConfig,
      tplUsageAnalyzerConfig,
      criticalMethodsRemoverConfig,
      tplMethodsRemoverConfig,
      deadCodeDetectorConfig,
      architectureValidatorConfig
    )
  }

  /**
   * Reads the option "projectJars" from the given json config
   * and performs some checks on it.
   *
   * @return List of File objects, retrieved from the paths given inside the option.
   */
  private def readProjectJarFiles(json: JsValue): Array[File] = {
    // projectJars: List[String]
    // - Required, should contain valid paths!
    val projectJarPaths: Array[String] = {
      val result = json \ "projectJars"
      if (result.isEmpty) handleOptionNotFound(true, "projectJars")
      result.get.as[Array[String]]
    }
    val projectJarFiles = projectJarPaths.map { path =>
      val projectFile = new File(path.replace('\\', '/'))
      if (!projectFile.exists) {
        logger.warn(s"projectJars: Path $path could not be read from or does not exist, therefore unusable.")
      }
      else if (!path.endsWith(".jar")) {
        logger.warn(s"projectJars: Path $path does not lead to a jar file, therefore most likely unusable.")
      }
      projectFile
    }

    projectJarFiles
  }

  /**
   * Reads the option "libraryJars" from the given json config
   * and performs some checks on it.
   *
   * @return List of File objects, retrieved from the paths given inside the option.
   */
  private def readLibraryJarFiles(json: JsValue): Array[File] = {
    val libraryJarPaths = {
      val result = json \ "libraryJars"
      if (result.isEmpty) handleOptionNotFound(true, "libraryJars")
      result.get.as[Array[String]]
    }
    val libraryJarFiles = libraryJarPaths.map { path =>
      val libraryFile = new File(path.replace('\\', '/'))
      if (!libraryFile.exists) {
        logger.warn(s"libraryJars: Path $path could not be read from or does not exist, therefore unusable.")
      }
      else if (!path.endsWith(".jar")) {
        logger.warn(s"libraryJars: Path $path does not lead to a jar file, therefore most likely unusable.")
      }
      libraryFile
    }

    libraryJarFiles
  }

  /**
   * Retrieve the values relevant for the GodClassDetectorConfig.
   */
  private def readGodClassDetectorConfig(json: JsValue): GodClassDetectorConfig = {
    val parentField = "godClassDetector"
    // Retrieve config for the god class detector
    val subAnalysisJson: JsValue = {
      val result = json \ parentField
      if (result.isEmpty) handleOptionNotFound(true, parentField)
      result.get
    }

    // Read config values
    val execute = readConfigValueWithoutDefault[Boolean](
      subJson = subAnalysisJson,
      field = "execute",
      parentField = parentField,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = true
    ).get
    val wmcThresh = readConfigValueWithDefault[Int](
      subJson = subAnalysisJson,
      field = "wmcThresh",
      parentField = parentField,
      default = GodClassDetectorConfig.DEFAULT_WMC_THRESH,
      expectedTypeDescription = "non-negative integer",
      isRequiredOption = execute
    )
    val tccThresh = readConfigValueWithDefault[Double](
      subJson = subAnalysisJson,
      field = "tccThresh",
      parentField = parentField,
      default = GodClassDetectorConfig.DEFAULT_TCC_THRESH,
      expectedTypeDescription = "decimal value between 0 and 1",
      isRequiredOption = execute
    )
    val atfdThresh = readConfigValueWithDefault[Int](
      subJson = subAnalysisJson,
      field = "atfdThresh",
      parentField = parentField,
      default = GodClassDetectorConfig.DEFAULT_ATFD_THRESH,
      expectedTypeDescription = "non-negative integer",
      isRequiredOption = execute
    )
    val nofThresh = readConfigValueWithDefault[Int](
      subJson = subAnalysisJson,
      field = "nofThresh",
      parentField = parentField,
      default = GodClassDetectorConfig.DEFAULT_NOF_THRESH,
      expectedTypeDescription = "non-negative integer",
      isRequiredOption = execute
    )

    GodClassDetectorConfig(execute, wmcThresh, tccThresh, atfdThresh, nofThresh)
  }

  /**
   * Retrieve the values relevant for the CriticalMethodsDetectorConfig.
   */
  private def readCriticalMethodsDetectorConfig(json: JsValue): CriticalMethodsDetectorConfig = {
    val parentField = "criticalMethodsDetector"
    val subAnalysisJson: JsValue = {
      val result = json \ parentField
      if (result.isEmpty) handleOptionNotFound(true, parentField)
      result.get
    }

    // Read config values
    val execute = readConfigValueWithoutDefault[Boolean](
      subJson = subAnalysisJson,
      field = "execute",
      parentField = parentField,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = true
    ).get
    val criticalMethods = readConfigValueWithDefault[List[SelectedMethodsOfClass]](
      subJson = subAnalysisJson,
      field = "criticalMethods",
      parentField = parentField,
      default = CriticalMethodsDetectorConfig.DEFAULT_CRITICAL_METHODS,
      expectedTypeDescription = "list of {\"className\": String, \"selectedMethods\": List[String]}",
      isRequiredOption = execute
    )
    val ignore = readConfigValueWithDefault[Set[IgnoredCall]](
      subJson = subAnalysisJson,
      field = "ignore",
      parentField = parentField,
      default = CriticalMethodsDetectorConfig.DEFAULT_IGNORE,
      expectedTypeDescription = "list of {\"callerClass\": String, \"callerMethod\": String, \"targetClass\": String, \"targetMethod\": String}",
      isRequiredOption = execute
    )
    val callGraphAlgorithmName = readCallGraphAlgorithmName(
      subJson = subAnalysisJson,
      parentField = parentField,
      default = CriticalMethodsDetectorConfig.DEFAULT_CALL_GRAPH_ALGORITHM_NAME,
      isRequiredOption = execute
    )
    val entryPointsFinder = readEntryPointsFinder(
      subJson = subAnalysisJson,
      parentField = parentField,
      default = CriticalMethodsDetectorConfig.DEFAULT_ENTRY_POINTS_FINDER,
      isRequiredOption = execute
    )
    val customEntryPoints = readConfigValueWithDefault(
      subJson = subAnalysisJson,
      field = "customEntryPoints",
      parentField = parentField,
      default = CriticalMethodsDetectorConfig.DEFAULT_CUSTOM_ENTRY_POINTS,
      expectedTypeDescription = "list of {\"className\": String, \"selectedMethods\": List[String]}",
      isRequiredOption = execute
    )

    CriticalMethodsDetectorConfig(execute, criticalMethods, ignore, callGraphAlgorithmName, entryPointsFinder, customEntryPoints)
  }

  /**
   * Retrieve the values relevant for the TPLUsageAnalyzerConfig.
   */
  private def readTPLUsageAnalyzerConfig(json: JsValue, criticalMethodsDetectorConfig: CriticalMethodsDetectorConfig): TPLUsageAnalyzerConfig = {
    val parentField = "tplUsageAnalyzer"
    val subAnalysisJson: JsValue = {
      val result = json \ parentField
      if (result.isEmpty) handleOptionNotFound(true, parentField)
      result.get
    }

    // Read config values
    val execute = readConfigValueWithoutDefault[Boolean](
      subJson = subAnalysisJson,
      field = "execute",
      parentField = parentField,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = true
    ).get
    val countAllMethods = readConfigValueWithDefault[Boolean](
      subJson = subAnalysisJson,
      field = "countAllMethods",
      parentField = parentField,
      default = TPLUsageAnalyzerConfig.DEFAULT_COUNT_ALL_METHODS,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = execute
    )
    val callGraphAlgorithmName = readCallGraphAlgorithmName(
      subJson = subAnalysisJson,
      parentField = parentField,
      default = criticalMethodsDetectorConfig.callGraphAlgorithmName,
      isRequiredOption = execute
    )
    val entryPointsFinder = readEntryPointsFinder(
      subJson = subAnalysisJson,
      parentField = parentField,
      default = criticalMethodsDetectorConfig.entryPointsFinder,
      isRequiredOption = execute
    )
    val customEntryPoints = readConfigValueWithDefault(
      subJson = subAnalysisJson,
      field = "customEntryPoints",
      parentField = parentField,
      default = criticalMethodsDetectorConfig.customEntryPoints,
      expectedTypeDescription = "list of {\"className\": String, \"selectedMethods\": List[String]}",
      isRequiredOption = execute
    )

    TPLUsageAnalyzerConfig(execute , countAllMethods, callGraphAlgorithmName, entryPointsFinder, customEntryPoints)
  }

  /**
   * Retrieve the values relevant for the CriticalMethodsRemoverConfig.
   */
  private def readCriticalMethodsRemoverConfig(json: JsValue, criticalMethodsDetectorConfig: CriticalMethodsDetectorConfig): CriticalMethodsRemoverConfig = {
    val parentField = "criticalMethodsRemover"
    val subAnalysisJson: JsValue = {
      val result = json \ parentField
      if (result.isEmpty) handleOptionNotFound(true, parentField)
      result.get
    }

    // Read config values
    val execute = readConfigValueWithoutDefault[Boolean](
      subJson = subAnalysisJson,
      field = "execute",
      parentField = parentField,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = true
    ).get
    val criticalMethods = readConfigValueWithDefault[List[SelectedMethodsOfClass]](
      subJson = subAnalysisJson,
      field = "criticalMethods",
      parentField = parentField,
      default = criticalMethodsDetectorConfig.criticalMethods,
      expectedTypeDescription = "list of {\"className\": String, \"selectedMethods\": List[String]}",
      isRequiredOption = execute
    )
    val ignore = readConfigValueWithDefault[Set[IgnoredCall]](
      subJson = subAnalysisJson,
      field = "ignore",
      parentField = parentField,
      default = criticalMethodsDetectorConfig.ignore,
      expectedTypeDescription = "list of {\"callerClass\": String, \"callerMethod\": String, \"targetClass\": String, \"targetMethod\": String}",
      isRequiredOption = execute
    )

    CriticalMethodsRemoverConfig(execute, criticalMethods, ignore)
  }

  /**
   * Retrieve the values relevant for the TPLMethodsRemoverConfig.
   */
  private def readTPLMethodsRemoverConfig(json: JsValue, tplUsageAnalyzerConfig: TPLUsageAnalyzerConfig): TPLMethodsRemoverConfig = {
    val parentField = "tplMethodsRemover"
    val subAnalysisJson: JsValue = {
      val result = json \ parentField
      if (result.isEmpty) handleOptionNotFound(true, parentField)
      result.get
    }

    // Read config values
    val execute = readConfigValueWithoutDefault[Boolean](
      subJson = subAnalysisJson,
      field = "execute",
      parentField = parentField,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = true
    ).get
    // Default behavior for TPLMethodsRemover: Choose a random TPL to create a dummy from
    // This should be implemented inside the TPLMethodsRemover
    val tplJar = readConfigValueWithDefault[String](
      subJson = subAnalysisJson,
      field = "tplJar",
      parentField = parentField,
      default = "DEFAULT",
      expectedTypeDescription = "string path copied from libraryJars inside this config json",
      isRequiredOption = execute
    )
    val includeNonPublicMethods = readConfigValueWithDefault[Boolean](
      subJson = subAnalysisJson,
      field = "includeNonPublicMethods",
      parentField = parentField,
      default = TPLMethodsRemoverConfig.DEFAULT_INCLUDE_PUBLIC_METHODS,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = execute
    )
    val callGraphAlgorithmName = readCallGraphAlgorithmName(
      subJson = subAnalysisJson,
      parentField = parentField,
      default = tplUsageAnalyzerConfig.callGraphAlgorithmName,
      isRequiredOption = execute
    )
    val entryPointsFinder = readEntryPointsFinder(
      subJson = subAnalysisJson,
      parentField = parentField,
      default = tplUsageAnalyzerConfig.entryPointsFinder,
      isRequiredOption = execute
    )
    val customEntryPoints = readConfigValueWithDefault(
      subJson = subAnalysisJson,
      field = "customEntryPoints",
      parentField = parentField,
      default = tplUsageAnalyzerConfig.customEntryPoints,
      expectedTypeDescription = "list of {\"className\": String, \"selectedMethods\": List[String]}",
      isRequiredOption = execute
    )

    TPLMethodsRemoverConfig(execute, tplJar, includeNonPublicMethods, callGraphAlgorithmName, entryPointsFinder, customEntryPoints)
  }

  /**
   * Retrieve the values relevant for the DeadCodeDetectorConfig.
   */
  private def readDeadCodeDetectorConfig(json: JsValue): DeadCodeDetectorConfig = {
    val parentField = "deadCodeDetector"
    val subAnalysisJson: JsValue = {
      val result = json \ parentField
      if (result.isEmpty) handleOptionNotFound(true, parentField)
      result.get
    }

    // Read config values
    val execute = readConfigValueWithoutDefault[Boolean](
      subJson = subAnalysisJson,
      field = "execute",
      parentField = parentField,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = true
    ).get
    val completelyLoadLibraries = readConfigValueWithDefault[Boolean](
      subJson = subAnalysisJson,
      field = "completelyLoadLibraries",
      parentField = parentField,
      default = DeadCodeDetectorConfig.DEFAULT_COMPLETELY_LOAD_LIBRARIES,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = execute
    )

    DeadCodeDetectorConfig(execute, completelyLoadLibraries)
  }

  private def readArchitectureValidatorConfig(json: JsValue): ArchitectureValidatorConfig = {
    val parentField = "architectureValidator"
    val subAnalysisJson: JsValue = {
      val result = json \ parentField
      if (result.isEmpty) handleOptionNotFound(true, parentField)
      result.get
    }

    // Read config values
    val execute = readConfigValueWithoutDefault[Boolean](
      subJson = subAnalysisJson,
      field = "execute",
      parentField = parentField,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = true
    ).get
    val onlyMethodAndFieldAccesses = readConfigValueWithDefault[Boolean](
      subJson = subAnalysisJson,
      field = "onlyMethodAndFieldAccesses",
      parentField = parentField,
      default = ArchitectureValidatorConfig.DEFAULT_ONLY_METHOD_AND_FIELD_ACCESSES,
      expectedTypeDescription = "boolean, i.e. true or false",
      isRequiredOption = execute
    )
    val defaultRule = readConfigValueWithDefault[String](
      subJson = subAnalysisJson,
      field = "defaultRule",
      parentField = parentField,
      default = ArchitectureValidatorConfig.DEFAULT_DEFAULT_RULE,
      expectedTypeDescription = "string \"ALLOWED\" or \"FORBIDDEN\"",
      isRequiredOption = execute
    )
    if (defaultRule != "ALLOWED" && defaultRule != "FORBIDDEN") {
      handleOptionValueInvalid(
        isRequiredOption = execute,
        message = s"Expected string \"ALLOWED\" or \"FORBIDDEN\" or \"DEFAULT\", but received $defaultRule",
        parentField, "defaultRule"
      )
    }
    val rules = readConfigValueWithDefault[List[Rule]](
      subJson = subAnalysisJson,
      field = "rules",
      parentField = parentField,
      default = ArchitectureValidatorConfig.DEFAULT_RULES,
      expectedTypeDescription = "list of Rule objects (i.e. {\"from\": String, \"to\": String, \"type\": \"ALLOWED\" or \"FORBIDDEN\", \"except\": Optional List[Rule]})",
      isRequiredOption = execute
    )

    ArchitectureValidatorConfig(execute, onlyMethodAndFieldAccesses, defaultRule, rules)
  }

  /**
   * Logs that an expected option is not present in the given config json.
   *
   * @param isRequiredOption If true, print out error and terminate entire analysis
   *                         (i.e. never returns). If false, just print out warning.
   * @param location         Option that is missing in the config json. If it is a nested value, provide each option down to the
   *                         actually missing value as a separate string.
   */
  private def handleOptionNotFound(isRequiredOption: Boolean, location: String*): Unit = {
    val locationString = location.mkString("\"", "\" \\ \"", "\"")
    if (isRequiredOption) {
      logger.error(s"Could not retrieve option $locationString from the config json.")
      logger.error("Tip: Use -initializeConfig to create a default config and use it as a template to create your own config.")
      logger.error("     For more information on how to configure the analyses, look into the README file for this program.")
      logger.error("Terminating analysis...")
      System.exit(1)
      // Should be unreachable
      throw new NoSuchElementException(s"Error in $locationString: Option missing.")
    }
    else {
      logger.warn(s"Could not retrieve option $locationString from the config json, ignoring as not required for this configuration.")
      logger.warn("Tip: Use -initializeConfig to create a default config and use it as a template to create your own config.")
      logger.warn("     For more information on how to configure the analyses, look into the README file for this program.")
    }
  }

  /**
   * Shows an error saying that an option received an invalid value.
   * Terminates the entire analysis after logging the error.
   *
   * @param isRequiredOption If true, print out error and terminate entire analysis
   *                         (i.e. never returns). If false, just print out warning.
   * @param message          The message to show what when wrong (e.g. what type was expected).
   * @param location         Option that is missing in the config json. If it is a nested value, provide each option down to the
   *                         actually missing value as a separate string.
   */
  private def handleOptionValueInvalid(isRequiredOption: Boolean, message: String, location: String*): Unit = {
    val locationString = location.mkString("\"", "\" \\ \"", "\"")
    if (isRequiredOption) {
      logger.error(s"$locationString: $message.")
      logger.error("Terminating analysis...")
      System.exit(1)
      // Should be unreachable
      throw new NoSuchElementException(s"Error in $locationString: Invalid value for option provided.")
    }
    else {
      logger.warn(s"$locationString: $message, ignoring as not required for this configuration.")
    }
  }

  /**
   * Reads a value from a sub-config inside the analysis json (i.e. a config for a single subanalysis, e.g.
   * ''GodClassDetector''). Accepts a value with type `T`, doesn't provide any default value.
   *
   * '''Note:''' This function only checks if the value has the correct type, not whether the value is valid for the
   * sub-analysis this value will be used in (e.g. check whether the int is non-negative).
   * '''This must be checked inside the sub-analysis and handled accordingly!'''
   *
   * @param subJson                 The sub-config to read the value from.
   * @param field                   The value to read from subJson.
   * @param parentField             The value the subJson was retrieved from (e.g. `"GodClassDetector"`)
   * @param expectedTypeDescription Expected type for the value (next to `"DEFAULT"`), written as a string.
   * @param isRequiredOption        Whether this option is required for this analysis run. If `true`, the program will terminate
   *                                and error if the option is either missing or invalid. If `false`, the program will just
   *                                log a warning, insert the (not needed) default value and continue reading the config
   *                                or executing the analysis.
   * @param reads                   Instructions for play-json on how to read the value.
   * @tparam T The expected type for the value.
   * @return Option with type T that ''may'' contain the read value. May also never return if it is a required option and
   *         the value is either missing or invalid.
   */
  private def readConfigValueWithoutDefault[T]
  (
    subJson: JsValue,
    field: String,
    parentField: String,
    expectedTypeDescription: String,
    isRequiredOption: Boolean
  )(implicit reads: Reads[T]): Option[T] = subJson \ field match {
    case JsDefined(jsValue) =>
      jsValue.validate[T] match {
        // Value has expected type
        case JsSuccess(value, _) => Some(value)
        // Value has wrong type, log error or warning
        case JsError(_) =>
          val message = s"Expected $expectedTypeDescription or \"DEFAULT\""
          handleOptionValueInvalid(isRequiredOption, message, parentField, field)
          None // Return something in case just a warning was printed out and option was not required
      }
    // Field not found, log error or warning
    case _: JsUndefined =>
      handleOptionNotFound(isRequiredOption, parentField, field)
      None // Return something in case just a warning was printed out and option was not required
  }


  /**
   * Reads a value from a sub-config inside the analysis json (i.e. a config for a single subanalysis, e.g.
   * ''GodClassDetector''). Accepts a value with type `T` or a string `"DEFAULT"` that inserts the corresponding
   * default value.
   *
   * '''Note:''' This function only checks if the value has the correct type, not whether the value is valid for the
   * sub-analysis this value will be used in (e.g. check whether the int is non-negative).
   * '''This must be checked inside the sub-analysis and handled accordingly!'''
   *
   * @param subJson                 The sub-config to read the value from.
   * @param field                   The value to read from subJson.
   * @param parentField             The value the subJson was retrieved from (e.g. `"GodClassDetector"`)
   * @param default                 The default value to use if `"DEFAULT"` was entered.
   * @param expectedTypeDescription Expected type for the value (next to `"DEFAULT"`), written as a string.
   * @param isRequiredOption        Whether this option is required for this analysis run. If `true`, the program will terminate
   *                                and error if the option is either missing or invalid. If `false`, the program will just
   *                                log a warning, insert the (not needed) default value and continue reading the config
   *                                or executing the analysis.
   * @param reads                   Instructions for play-json on how to read the value.
   * @tparam T The expected type for the value.
   * @return Either the valid read value or the default value. May also never return if it is a required option and
   *         the value is either missing or invalid.
   */
  private def readConfigValueWithDefault[T]
  (
    subJson: JsValue,
    field: String,
    parentField: String,
    default: T,
    expectedTypeDescription: String,
    isRequiredOption: Boolean
  )(implicit reads: Reads[T]): T = subJson \ field match {
    // Field found and contains "DEFAULT"
    case JsDefined(JsString("DEFAULT")) =>
      default
    // Field found and contains some value
    case JsDefined(jsValue) =>
      jsValue.validate[T] match {
        // Value has expected type
        case JsSuccess(value, _) => value
        // Value has wrong type, log error or warning
        case JsError(_) =>
          val message = s"Expected $expectedTypeDescription or \"DEFAULT\""
          handleOptionValueInvalid(isRequiredOption, message, parentField, field)
          default // Return default value in case just a warning was printed out
      }
    // Field not found, log error or warning
    case _: JsUndefined =>
      handleOptionNotFound(isRequiredOption, parentField, field)
      default // Return default value in case just a warning was printed out
  }

  /**
   * Reads the call graph algorithm name from a sub-config inside the analysis json (i.e. a config for a single subanalysis, e.g.
   * ''CriticalMethodsDetector''). Only accepts strings "CHA", "RTA", "XTA", "CTA", "1-1-CFA" (uppercase or lowercase
   * irrelevant, multiple writing styles for 1-1-CFA) or "DEFAULT" (inserts the corresponding default value).
   *
   * @param subJson          The sub-config to read the value from.
   * @param field            The value to read from subJson (should be "callGraphAlgorithmName").
   * @param parentField      The value the subJson was retrieved from (e.g. `"CriticalMethodsDetector"`)
   * @param default          The default value to use if `"DEFAULT"` was entered.
   * @param isRequiredOption Whether this option is required for this analysis run. If `true`, the program will terminate
   *                         and error if the option is either missing or invalid. If `false`, the program will just
   *                         log a warning, insert the (not needed) default value and continue reading the config
   *                         or executing the analysis.
   * @return Either the valid read value or the default value. May also never return if it is a required option and
   *         the value is either missing or invalid.
   */
  private def readCallGraphAlgorithmName
  (
    subJson: JsValue,
    field: String = "callGraphAlgorithmName",
    parentField: String,
    default: String,
    isRequiredOption: Boolean
  ): String = {
    var callGraphAlgorithmName = readConfigValueWithDefault[String](
      subJson = subJson,
      field = field,
      parentField = parentField,
      default = default,
      expectedTypeDescription = "string \"CHA\", \"RTA\", \"XTA\", \"CTA\", \"1-1-CFA\"",
      isRequiredOption = isRequiredOption
    ).toLowerCase
    // Allow multiple writing styles for 1-1-CFA
    if (callGraphAlgorithmName == "1-1-cfa" ||
      callGraphAlgorithmName == "1_1_cfa" ||
      callGraphAlgorithmName == "cfa-1-1" ||
      callGraphAlgorithmName == "cfa_1_1"
    ) callGraphAlgorithmName = "1-1-cfa"
    // Check if call graph algorithm name valid
    if (callGraphAlgorithmName != "cha" &&
      callGraphAlgorithmName != "rta" &&
      callGraphAlgorithmName != "xta" &&
      callGraphAlgorithmName != "cta" &&
      callGraphAlgorithmName != "1-1-cfa"
    ) handleOptionValueInvalid(
      isRequiredOption = isRequiredOption,
      message = s"Expected string \"CHA\", \"RTA\", \"XTA\", \"CTA\", \"1-1-CFA\" or \"DEFAULT\", but received $callGraphAlgorithmName",
      parentField, "callGraphAlgorithmName"
    )
    callGraphAlgorithmName
  }

  /**
   * Reads the entry points finder from a sub-config inside the analysis json (i.e. a config for a single subanalysis, e.g.
   * ''CriticalMethodsDetector''). Only accepts strings "custom", "application", "applicationWithJre", "library"
   * (uppercase or lowercase irrelevant) or "DEFAULT" (inserts the corresponding default value).
   *
   * @param subJson          The sub-config to read the value from.
   * @param field            The value to read from subJson (should be "entryPointsFinder").
   * @param parentField      The value the subJson was retrieved from (e.g. `"CriticalMethodsDetector"`)
   * @param default          The default value to use if `"DEFAULT"` was entered.
   * @param isRequiredOption Whether this option is required for this analysis run. If `true`, the program will terminate
   *                         and error if the option is either missing or invalid. If `false`, the program will just
   *                         log a warning, insert the (not needed) default value and continue reading the config
   *                         or executing the analysis.
   * @return Either the valid read value or the default value. May also never return if it is a required option and
   *         the value is either missing or invalid.
   */
  private def readEntryPointsFinder
  (
    subJson: JsValue,
    field: String = "entryPointsFinder",
    parentField: String,
    default: String,
    isRequiredOption: Boolean
  ): String = {
    val entryPointsFinder = readConfigValueWithDefault[String](
      subJson = subJson,
      field = field,
      parentField = parentField,
      default = default,
      expectedTypeDescription = "string \"custom\", \"application\", \"applicationWithJre\", \"library\"",
      isRequiredOption = isRequiredOption
    ).toLowerCase
    // Check if entry points finder valid
    if (entryPointsFinder != "custom" &&
      entryPointsFinder != "application" &&
      entryPointsFinder != "applicationwithjre" &&
      entryPointsFinder != "library"
    ) handleOptionValueInvalid(
      isRequiredOption = isRequiredOption,
      message = s"Expected string \"custom\", \"application\", \"applicationWithJre\", \"library\" or \"DEFAULT\", but received $entryPointsFinder",
      parentField, "entryPointsFinder"
    )

    entryPointsFinder
  }

  /**
   * Writes the summary for the entire analysis suite at the given path.
   *
   * @param summary The Summary of this analysis suite run.
   * @param path Path to write the summary to.
   */
  def writeSummary(summary: Summary, path: String): Unit = {
    val json = Json.toJson(summary)
    val writer = new PrintWriter(new File(path))
    writer.write(Json.prettyPrint(json))
    writer.close()
  }
}

object JsonIO {
  /**
   * Folder "analysis" inside the current directory
   * where the results get written to by default.
   */
  private val DEFAULT_OUTPUT_DIRECTORY: String = "analysis"

  /** File "config.json" inside the current directory. */
  val DEFAULT_INPUT_JSON_PATH: String = "config.json"
}
