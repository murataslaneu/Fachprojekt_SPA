package analyses.A_GodClassDetector.data

import play.api.libs.json.{Json, OFormat}

case class JsonReport
(
  projectJars: Array[String],
  wmcThreshold: Int,
  tccThreshold: Double,
  atfdThreshold: Int,
  nofThreshold: Int,
  godClasses: List[GodClass]
)

object JsonReport {
  implicit val format: OFormat[JsonReport] = Json.format[JsonReport]
}
