package data

import play.api.libs.json.{Json, OFormat}

/**
 * Data model for architecture violation report
 */
case class ArchitectureViolation(
                                  fromClass: String,
                                  toClass: String,
                                  fromPackage: String,
                                  toPackage: String,
                                  fromJar: String,
                                  toJar: String,
                                  violationType: String, // "METHOD_CALL", "FIELD_ACCESS", "INHERITANCE", "INTERFACE_IMPLEMENTATION", "FIELD_TYPE", "RETURN_TYPE", "PARAMETER_TYPE"
                                  methodSignature: Option[String] = None,
                                  fieldName: Option[String] = None,
                                  lineNumber: Option[Int] = None
                                )

case class ArchitectureReport(
                               filesAnalyzed: List[String],
                               specificationFile: String,
                               timeFinished: java.time.LocalDateTime,
                               totalRuntimeMs: Long,
                               violations: List[ArchitectureViolation],
                               warnings: List[String]
                             )

object ArchitectureViolation {
  implicit val format: OFormat[ArchitectureViolation] = Json.format[ArchitectureViolation]
}

object ArchitectureReport {
  implicit val format: OFormat[ArchitectureReport] = Json.format[ArchitectureReport]
}

/**
 * Configuration for architecture validation analysis
 */
case class ArchitectureConfig(
                               projectJars: List[java.io.File],
                               libraryJars: List[java.io.File],
                               specificationFile: String,
                               outputJson: String,
                               completelyLoadLibraries: Boolean = false
                             )