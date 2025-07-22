package analyses.E_DeadCodeDetector.data

import play.api.libs.json.{Json, OFormat}

/**
 * Output of the analysis giving a full report on where dead code inside the analyzed project is located at.
 *
 * @param projectJars The project jar files loaded for this analysis.
 * @param libraryJars The library jar files loaded for this analysis.
 * @param completelyLoadedLibraries Whether the libraries were loaded completely (`true`)
 *                                  or only as interfaces (`false`) during the analysis.
 * @param methodsFound List of all methods that contain at least one dead instruction
 */
case class MultiDomainDeadCodeReport
(
  projectJars: Array[String],
  libraryJars: Array[String],
  completelyLoadedLibraries: Boolean,
  totalMethodsWithDeadInstructions: Int,
  totalDeadInstructions: Int,
  methodsFound: List[MultiDomainMethodWithDeadCode]
)

object MultiDomainDeadCodeReport {
  implicit val format: OFormat[MultiDomainDeadCodeReport] = Json.format[MultiDomainDeadCodeReport]
}
