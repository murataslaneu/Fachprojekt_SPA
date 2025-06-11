package modify.data

import play.api.libs.json._

/**
 * Represents a single analysis result for a method.
 *
 * @param className     Fully qualified name of the class containing the method (e.g., "com.example.MyClass")
 * @param methodName    Name of the method being analyzed (e.g., "main", "initSecurity")
 * @param removedCalls  List of critical method names that were detected and removed from the bytecode of this method
 *
 * This data structure is later serialized into a JSON output,
 * to report which critical calls were eliminated during analysis.
 */
case class AnalysisResult(
                           className: String,
                           methodName: String,
                           removedCalls: List[String]
                         )

/**
 * Provides automatic JSON (de)serialization for the AnalysisResult case class,
 * allowing it to be converted to/from JSON when reading or writing result files.
 */
object AnalysisResult {
  implicit val format: OFormat[AnalysisResult] = Json.format[AnalysisResult]
}