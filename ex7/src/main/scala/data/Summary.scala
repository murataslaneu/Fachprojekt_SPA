package data

import play.api.libs.json.{Json, OFormat}

/**
 * Object summarizing what was executed in this analysis suite run.
 *
 * @param totalRunTimeSeconds Total runtime from start to finish for the entire program run in seconds.
 * @param timeFinished Time at which the analysis suite finished.
 * @param analysesExecuted List of sub-analyses that were executed.
 */
case class Summary
(
  totalRunTimeSeconds: Double,
  timeFinished: java.time.LocalDateTime,
  analysesExecuted: List[SubAnalysisRun]
)

object Summary {
  implicit val format: OFormat[Summary] = Json.format[Summary]
}
