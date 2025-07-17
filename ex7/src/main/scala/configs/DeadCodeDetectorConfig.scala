package configs

/**
 * Analysis 5: Dead code detector (ex5), config
 *
 * @param execute Whether to execute the sub-analysis (`true`) or not (`false`).
 * @param completelyLoadLibraries Whether to completely load the library jars (`true`) or not (`false`).
 */
case class DeadCodeDetectorConfig
(
  override val execute: Boolean,
  completelyLoadLibraries: Boolean = true
) extends SubAnalysisConfig()
