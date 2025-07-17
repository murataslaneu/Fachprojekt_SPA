package configs

import data.SelectedMethodsOfClass
import org.opalj.tac.cg.CallGraphKey

/**
 * Analysis 4b: Third party library methods remover (ex4, part 2), Config
 *
 * @param execute Whether to execute the sub-analysis (`true`) or not (`false`).
 * @param tplJar The library jar that should be used to create a dummy that only contains the used methods.
 * @param includeNonPublicMethods Whether to only include public methods (`false`) or also non-public methods (e.g.
 *                                private methods that get called indirectly) (`true`).
 * @param callGraphAlgorithm The call graph algorithm to use for this analysis.
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
case class TPLMethodsRemoverConfig
(
  override val execute: Boolean,
  tplJar: String,
  includeNonPublicMethods: Boolean = true,
  callGraphAlgorithm: CallGraphKey,
  entryPointsFinder: String,
  customEntryPoints: List[SelectedMethodsOfClass]
) extends SubAnalysisConfig()
