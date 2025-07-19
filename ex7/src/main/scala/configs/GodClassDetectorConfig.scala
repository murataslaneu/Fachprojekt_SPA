package configs

/**
 * Analysis 1: God class detector (ex1), Config
 *
 * @param execute Whether to execute the sub-analysis (`true`) or not (`false`).
 * @param wmcThresh Threshold for the Weighted Methods per Class (WMC): Number of methods
 * @param tccThresh Threshold for the Tight Class Cohesion (TCC): Ratio of method pairs that share instance variables
 * @param atfdThresh Threshold for the Access to Foreign Data (ATFD): Number of accesses to fields from other classes
 * @param nofThresh Threshold for the Number of Fields (NOF): Number of fields in the class
 */
case class GodClassDetectorConfig
(
  override val execute: Boolean,
  wmcThresh: Int,
  tccThresh: Double,
  atfdThresh: Int,
  nofThresh: Int
) extends SubAnalysisConfig()

object GodClassDetectorConfig {
  val DEFAULT_WMC_THRESH: Int = 100
  val DEFAULT_TCC_THRESH: Double = 0.33
  val DEFAULT_ATFD_THRESH: Int = 8
  val DEFAULT_NOF_THRESH: Int = 30
}
