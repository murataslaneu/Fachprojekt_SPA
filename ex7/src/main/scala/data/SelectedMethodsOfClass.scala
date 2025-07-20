package data

import play.api.libs.json.{Json, OFormat}

/**
 * Holds a list of selected methods for a class. Used for either storing the critical methods of a class or the entry
 * points of a class.
 *
 * @param className Name of the class
 * @param methods Corresponding methods names of the class which have been selected.
 */
case class SelectedMethodsOfClass(var className: String, var methods: List[String])

object SelectedMethodsOfClass {
  implicit val format: OFormat[SelectedMethodsOfClass] = Json.format[SelectedMethodsOfClass]
}
