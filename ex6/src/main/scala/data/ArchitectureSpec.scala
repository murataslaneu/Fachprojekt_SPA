package data

import play.api.libs.json.{Json, OFormat}

/**
 * Data model for architecture specification
 */
case class ArchitectureSpec(
                             defaultRule: String, // "ALLOWED" or "FORBIDDEN"
                             rules: List[Rule]
                           )

case class Rule(
                 from: String,
                 to: String,
                 `type`: String, // "ALLOWED" or "FORBIDDEN"
                 except: Option[List[Rule]] = None
               )

object ArchitectureSpec {
  implicit val ruleFormat: OFormat[Rule] = Json.format[Rule]
  implicit val format: OFormat[ArchitectureSpec] = Json.format[ArchitectureSpec]
}