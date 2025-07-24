package analyses.A_GodClassDetector.data

import play.api.libs.json.{Json, OFormat}

case class GodClass
(
  className: String,
  jar: String,
  wmc: Int,
  tcc: Double,
  atfd: Int,
  nof: Int
)

object GodClass {
  implicit val format: OFormat[GodClass] = Json.format[GodClass]
}
