package analyses.B_CriticalMethodsDetector.data

import data.{IgnoredCall, SelectedMethodsOfClass}
import play.api.libs.json.{Json, OFormat}

case class JsonReport
(
  projectJars: Array[String],
  libraryJars: Array[String],
  criticalMethods: List[SelectedMethodsOfClass],
  ignore: Set[IgnoredCall],
  callGraphAlgorithmUsed: String,
  usedEntryPointsFinder: String,
  customEntryPoints: List[SelectedMethodsOfClass],
  criticalCallsFound: Int,
  criticalCalls: List[CriticalCall]
)

object JsonReport {
  implicit val format: OFormat[JsonReport] = Json.format[JsonReport]
}
