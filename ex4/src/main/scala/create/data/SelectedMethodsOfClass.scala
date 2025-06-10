package create.data

import play.api.libs.json.{Format, Json}

/**
 * Holds a list of selected methods for a class. Used for storing the entry points of a class.
 *
 * @param className Name of the class
 * @param methods Corresponding method names of the class which have been selected.
 */
case class SelectedMethodsOfClass(var className: String, var methods: List[String])

object SelectedMethodsOfClass {
  implicit val format: Format[SelectedMethodsOfClass] = Json.format[SelectedMethodsOfClass]
}
