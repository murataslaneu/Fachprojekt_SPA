package configs

/**
 * The main config for this application.
 *
 * The user should be able to configure the project from a single json file.
 * Most analysis parameters have default values to simplify the setup.
 */
case class StaticAnalysisConfig
(
  projectJars: List[String],
  libraryJars: List[String],
  resultsOutputPath: String,
  godClassDetector: GodClassDetectorConfig,
  criticalMethodsDetector: CriticalMethodsDetectorConfig,
  tplUsageAnalyzer: TPLUsageAnalyzerConfig,
  criticalMethodsRemover: CriticalMethodsRemoverConfig,
  tplMethodsRemover: TPLMethodsRemoverConfig,
  deadCodeDetector: DeadCodeDetectorConfig,
  architectureValidator: ArchitectureValidatorConfig
)
