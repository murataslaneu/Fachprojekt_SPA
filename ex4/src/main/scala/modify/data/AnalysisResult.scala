package modify.data

import play.api.libs.json._
import modify.data.RemovedCall

/**
 * Represents the result of analyzing and modifying a specific method.
 *
 * @param className        Fully qualified name of the class containing the method.
 * @param methodName       Name of the method that was analyzed.
 * @param removedCalls     List of critical method calls that were removed.
 * @param status           Status message about what was done (e.g., whether class was modified).
 * @param ignored          True if any critical calls were ignored due to ignore list.
 * @param bytecodeVerified True if the bytecode was successfully verified after modification.
 */
case class AnalysisResult(
                           className: String,
                           methodName: String,
                           removedCalls: List[RemovedCall],
                           status: String,
                           ignored: Boolean,
                           bytecodeVerified: Boolean
                         )

object AnalysisResult {
  // Enables automatic JSON serialization/deserialization for AnalysisResult
  implicit val format: OFormat[AnalysisResult] = Json.format[AnalysisResult]
}
