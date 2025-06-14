package modify.data

import play.api.libs.json._
import modify.data.RemovedCall

case class AnalysisResult(
                           className: String,
                           methodName: String,
                           removedCalls: List[RemovedCall],
                           status: String,
                           ignored: Boolean,
                           bytecodeVerified: Boolean
                         )

object AnalysisResult {
  implicit val format: OFormat[AnalysisResult] = Json.format[AnalysisResult]
}
