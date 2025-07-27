package analyses.F_ArchitectureValidator.data

import play.api.libs.json.{Json, OFormat}

case class Rule(
                 from: String,
                 to: String,
                 `type`: String, // "ALLOWED" or "FORBIDDEN"
                 except: Option[List[Rule]] = None
               )

object Rule {
  implicit val format: OFormat[Rule] = Json.format[Rule]
}