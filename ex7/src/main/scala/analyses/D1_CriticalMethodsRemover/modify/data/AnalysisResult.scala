package analyses.D1_CriticalMethodsRemover.modify.data

import play.api.libs.json._

/**
 * Represents the result of analyzing and modifying a specific method.
 *
 * @param className        Fully qualified name of the class containing the method.
 * @param method           Name of the method that was analyzed.
 * @param removedCalls     List of critical method calls that were removed.
 * @param path             Path where the modified class file was written to.
 * @param ignored          True if any critical calls were ignored due to ignore list.
 * @param bytecodeVerified True if the bytecode was successfully verified after modification.
 * @param nopReplacements (Optional) List of tuples marking which instruction indices (PCs)
 *                        were replaced with NOPs during modification, including the original instruction.
 */
case class AnalysisResult(
                           className: String,
                           method: String,
                           fromJar: String,
                           removedCalls: List[RemovedCall],
                           path: String,
                           ignored: Boolean,
                           bytecodeVerified: Boolean,
                           nopReplacements: Option[List[(Int, String)]] = None
                         )


object AnalysisResult {
  // Enables automatic JSON serialization/deserialization for AnalysisResult
  implicit val format: OFormat[AnalysisResult] = Json.format[AnalysisResult]
}
