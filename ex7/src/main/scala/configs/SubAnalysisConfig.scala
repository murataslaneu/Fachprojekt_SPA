package configs

/**
 * A sub-analysis must always have a toggle whether it should be executed or not.
 */
trait SubAnalysisConfig {
  /** Whether to execute the sub-analysis (`true`) or not (`false`). */
  val execute: Boolean
}