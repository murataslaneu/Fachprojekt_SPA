package analysis

import play.api.libs.json._

/**
 * Result data model holding the final analysis output.
 *
 * @param analysis List of per-TPL usage results (one TPLInfo per library)
 * @param callGraphAlgorithm The algorithm name used for the call graph (e.g., "RTA")
 * @param analysisTimeSeconds The total analysis runtime in seconds
 */
case class TPLAnalysisResult(
analysis: List[TPLInfo],
callGraphAlgorithm: String,
analysisTimeSeconds: Double
)

object TPLAnalysisResult {
  // Play JSON serializer/deserializer for TPLAnalysisResult
  implicit val format: OFormat[TPLAnalysisResult] = Json.format[TPLAnalysisResult]
}
