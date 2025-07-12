package data

import play.api.libs.json.{Json, OFormat}

case class ArchitectureReport(
                               filesAnalyzed: List[String],
                               specificationFile: String,
                               timeFinished: java.time.LocalDateTime,
                               totalRuntimeMs: Long,
                               checkedOnlyMethodAndFieldAccesses: Boolean,
                               violations: List[Dependency],
                               warnings: List[String]
                             )

object ArchitectureReport {
  implicit val format: OFormat[ArchitectureReport] = Json.format[ArchitectureReport]
}
