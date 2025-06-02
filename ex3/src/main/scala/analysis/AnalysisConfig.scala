package analysis

import play.api.libs.json._

/**
 * Data model for reading the JSON config file.
 * Holds all analysis parameters.
 *
 * @param projectJar Path to the main project JAR file to be analyzed.
 * @param tplJars List of (direct) third-party library (TPL) JAR file paths to check for usage.
 * @param callGraphAlgorithm Name of the call graph algorithm to use ("RTA", "CHA", etc).
 * @param outputJson Optional output file name for results.
 * @param isLibraryProject Optional flag for OPAL: Treat the project as a library, not an application.
 *                         False by default.
 * @param countAllMethods Optional flag that determines whether only all methods should be looked at. If false, only
 *                        public methods are included in the numbers for all methods and used methods. False by default.
 */
case class AnalysisConfig(
projectJar: String,
tplJars: List[String],
callGraphAlgorithm: String = "RTA",
outputJson: Option[String] = None,
isLibraryProject: Boolean = false,
countAllMethods: Boolean = false,
)

object AnalysisConfig {
  // Play JSON serializer/deserializer for AnalysisConfig
  implicit val format: OFormat[AnalysisConfig] = Json.format[AnalysisConfig]
}
