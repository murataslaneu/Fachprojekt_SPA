package analysis

import play.api.libs.json._

/**
 * Data model for reading the JSON config file.
 * Holds all analysis parameters like project JAR, TPL JARs, etc.
 *
 * @param projectJar Path to the main project JAR file to be analyzed.
 * @param tplJars List of third-party library (TPL) JAR file paths to check for usage.
 * @param callGraphAlgorithm Name of the call graph algorithm to use ("RTA", "CHA", etc).
 * @param outputJson Optional output file name for results.
 * @param isLibraryProject Optional flag for OPAL: treat the project as a library, not an application.
 *                         Is false by default.
 */
case class AnalysisConfig(
projectJar: String,
tplJars: List[String],
callGraphAlgorithm: String = "RTA",
outputJson: Option[String] = None,
isLibraryProject: Boolean = false
)

object AnalysisConfig {
  // Play JSON serializer/deserializer for AnalysisConfig
  implicit val format: OFormat[AnalysisConfig] = Json.format[AnalysisConfig]
}
