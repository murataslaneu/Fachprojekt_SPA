package data

import play.api.libs.json.{Json, OFormat}

/**
 * Object logging for each sub-analysis run on what was executed, whether it was executed and more.
 *
 * @param analysisName The name of the sub-analysis executed.
 * @param successful Whether the sub-analysis finished without throwing an uncaught exception.
 * @param resultsPath Path where the results for this sub-analysis can be seen.
 * @param timeFinished Time at which this sub-analysis finished.
 * @param runTimeMs Run time for the sub-analysis in milliseconds.
 */
case class SubAnalysisRun
(
  analysisName: String,
  successful: Boolean,
  resultsPath: String,
  timeFinished: java.time.LocalDateTime,
  runTimeMs: Long
)

object SubAnalysisRun {
  implicit val format: OFormat[SubAnalysisRun] = Json.format[SubAnalysisRun]
}
