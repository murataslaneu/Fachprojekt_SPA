package analyses.D2_TPLMethodsRemover.create

import play.api.libs.json.{Json, OFormat}

/**
 * Case class holding the data for each written class file.
 *
 * @param className Fully qualified name of the class
 * @param usedMethods Number of methods this class contains. This may also depend on whether the dummy should
 *                    only contain public methods or also non-public methods.
 */
case class WrittenClassFile
(
  className: String,
  usedMethods: Int
)

object WrittenClassFile {
  implicit val format: OFormat[WrittenClassFile] = Json.format[WrittenClassFile]
}
