package configs

import data.SelectedMethodsOfClass

/**
 * Analysis 3: Third party library usage analyzer (ex3), Config
 *
 * @param execute Whether to execute the sub-analysis (`true`) or not (`false`).
 * @param countAllMethods Whether to only call public methods (`false`) or also private methods (`true`) that are called
 *                        indirectly.
 * @param callGraphAlgorithmName The call graph algorithm to use for this analysis.
 *                               (Available: "CHA", "RTA", "XTA", "CTA", "1-1-CFA")
 * @param entryPointsFinder What entry points finder to use for this analysis. Available:
 *                          - "custom": Use only custom entry points
 *                          - "application": All main methods inside the project
 *                          - "applicationWithJre": All main methods inside the project, including those added by the
 *                            (possibly) included JRE inside the project.
 *                           - "library": All methods accessible from the outside (including all public methods of the
 *                            project)
 * @param customEntryPoints List of (additional) methods (grouped by class) that should be treated as entry points by
 *                          the analysis.
 */
case class TPLUsageAnalyzerConfig
(
  override val execute: Boolean,
  countAllMethods: Boolean,
  callGraphAlgorithmName: String,
  entryPointsFinder: String,
  customEntryPoints: List[SelectedMethodsOfClass]
) extends SubAnalysisConfig()

object TPLUsageAnalyzerConfig {
  val DEFAULT_COUNT_ALL_METHODS: Boolean = false
}
