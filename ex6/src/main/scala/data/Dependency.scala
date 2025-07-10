package data

import play.api.libs.json.{Json, OFormat}

/**
 * Data model for architecture violation report
 */
case class Dependency(
                       fromClass: String,
                       toClass: String,
                       fromPackage: String,
                       toPackage: String,
                       fromJar: String,
                       toJar: String,
                       accessType: AccessType
                     )

object Dependency {
  implicit val format: OFormat[Dependency] = Json.format[Dependency]
}