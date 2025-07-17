package analyses.F_ArchitectureValidator.data

import play.api.libs.json.{Json, OFormat}

/**
 * Data model for architecture specification
 */
case class ArchitectureSpec(
                             defaultRule: String, // "ALLOWED" or "FORBIDDEN"
                             rules: List[Rule]
                           )



object ArchitectureSpec {
  implicit val ruleFormat: OFormat[Rule] = Json.format[Rule]
  implicit val format: OFormat[ArchitectureSpec] = Json.format[ArchitectureSpec]
}