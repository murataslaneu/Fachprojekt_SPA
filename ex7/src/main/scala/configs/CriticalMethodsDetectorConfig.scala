package configs

import data.{IgnoredCall, SelectedMethodsOfClass}
import org.opalj.tac.cg.{CallGraphKey, RTACallGraphKey}

/**
 * Analysis 2: Critical methods detector (ex2), Config
 *
 * @param execute Whether to execute the sub-analysis (`true`) or not (`false`).
 * @param criticalMethods List of all methods that should be treated as critical. Methods are grouped per class.
 * @param ignore List of all calls of (potentially) critical methods that should be ignored. Each list item specifies
 *               a (possibly critical) method of a class that is allowed to be called inside a method of another class.
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
case class CriticalMethodsDetectorConfig
(
  override val execute: Boolean,
  criticalMethods: List[SelectedMethodsOfClass] = List(
    SelectedMethodsOfClass("java.lang.System", List("getSecurityManager", "setSecurityManager")
    )
  ),
  ignore: List[IgnoredCall] = List.empty,
  callGraphAlgorithm: CallGraphKey = RTACallGraphKey,
  entryPointsFinder: String = "application",
  customEntryPoints: List[SelectedMethodsOfClass]
) extends SubAnalysisConfig()
