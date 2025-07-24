package analyses.B_CriticalMethodsDetector.data

import play.api.libs.json.{Json, OFormat}

case class CriticalCall
(
  fromClass: String,
  fromMethod: String,
  toClass: String,
  toMethod: String,
  numberOfCalls: Int
)

object CriticalCall {
  implicit val format: OFormat[CriticalCall] = Json.format[CriticalCall]
}
