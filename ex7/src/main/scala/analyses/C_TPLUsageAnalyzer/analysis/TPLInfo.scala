package analyses.C_TPLUsageAnalyzer.analysis

import play.api.libs.json._

/**
 * Holds analysis information for a single third-party library (TPL).
 *
 * @param library      The JAR file name (not full path)
 * @param totalMethods Number of public methods found in this TPL
 * @param usedMethods  Number of those methods used (called) by the analyzed project
 * @param usageRatio   Ratio (fraction) of used to total public methods (used/total)
 */
case class TPLInfo
(
  library: String,
  var totalMethods: Int,
  var usedMethods: Int,
  var usageRatio: Double
)

object TPLInfo {
  // Play JSON serializer/deserializer for TPLInfo
  implicit val format: OFormat[TPLInfo] = Json.format[TPLInfo]
}
