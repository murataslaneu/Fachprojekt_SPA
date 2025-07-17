package configs

import data.{IgnoredCall, SelectedMethodsOfClass}

/**
 * Analysis 4a: Critical methods detector (ex4, part 1), Config
 *
 * @param execute Whether to execute the sub-analysis (`true`) or not (`false`).
 * @param criticalMethods List of all methods that should be treated as critical. Methods are grouped per class.
 * @param ignore List of all calls of (potentially) critical methods that should be ignored. Each list item specifies
 *               a (possibly critical) method of a class that is allowed to be called inside a method of another class.
 */
case class CriticalMethodsRemoverConfig
(
  override val execute: Boolean,
  criticalMethods: List[SelectedMethodsOfClass],
  ignore: List[IgnoredCall]
) extends SubAnalysisConfig()
