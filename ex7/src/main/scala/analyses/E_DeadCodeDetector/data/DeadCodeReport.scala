package analyses.E_DeadCodeDetector.data

import play.api.libs.json.{Json, OFormat}

/**
 * Output of the analysis giving a full report on where dead code inside the analyzed project is located at.
 *
 * @param filesAnalyzed A list of all project jar files that have been analyzed
 * @param domainUsed Used domain by the abstract interpretation
 * @param timeFinished Date and time when the analyzed finished
 * @param totalRuntimeMs Runtime of the entire analysis in milliseconds
 *                       (beginning from starting the analysis via the terminal and ending when the analysis generated
 *                       the report)
 * @param methodsFound List of all methods that contain at least one dead instruction
 * */
case class DeadCodeReport(
                           filesAnalyzed: List[String],
                           domainUsed: String,
                           timeFinished: java.time.LocalDateTime,
                           totalRuntimeMs: Long,
                           methodsFound: List[MethodWithDeadCode]
                         )

object DeadCodeReport {
  implicit val format: OFormat[DeadCodeReport] = Json.format[DeadCodeReport]
}