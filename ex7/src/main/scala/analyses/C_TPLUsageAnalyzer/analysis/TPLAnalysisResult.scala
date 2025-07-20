package analyses.C_TPLUsageAnalyzer.analysis

import play.api.libs.json._

/**
 * Result data model holding the final analysis output.
 *
 * @param analysis            List of per-TPL usage results (one TPLInfo per library)
 * @param callGraphAlgorithm  The algorithm name used for the call graph (e.g., "RTA")
 * @param analysisTimeSeconds The total analysis runtime in seconds
 */
case class TPLAnalysisResult
(
  project: Array[String],
  analysis: List[TPLInfo],
  callGraphAlgorithm: String,
  var callGraphTimeSeconds: Double,
  var analysisTimeSeconds: Double,
  var subAnalysisTimeSeconds: Double,
)

object TPLAnalysisResult {
  // Play JSON serializer/deserializer for TPLAnalysisResult
  implicit val format: OFormat[TPLAnalysisResult] = Json.format[TPLAnalysisResult]
}
