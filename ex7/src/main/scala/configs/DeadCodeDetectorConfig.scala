package configs

/**
 * Analysis 5: Dead code detector (ex5), config
 *
 * @param execute Whether to execute the sub-analysis (`true`) or not (`false`).
 * @param completelyLoadLibraries Whether to completely load the library jars (`true`) or not (`false`).
 * @param domains Which domains (provided) by OPAL to use. In OPAL 5.0.0, there are a total of 13 domains available.
 *                The list can therefore contain numbers between 1 and 13 (inclusive).
 */
case class DeadCodeDetectorConfig
(
  override val execute: Boolean,
  completelyLoadLibraries: Boolean,
  domains: List[Int]
) extends SubAnalysisConfig()

object DeadCodeDetectorConfig {
  val DEFAULT_COMPLETELY_LOAD_LIBRARIES: Boolean = true
  val DEFAULT_DOMAINS: List[Int] = List(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13)
}
