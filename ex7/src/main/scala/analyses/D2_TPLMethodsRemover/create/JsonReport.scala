package analyses.D2_TPLMethodsRemover.create

import data.SelectedMethodsOfClass
import play.api.libs.json.{Json, OFormat}

/**
 * Report for the TPLMethodsRemover giving information on what the configuration for the analysis was,
 * how and when it finished and what used class files were found.
 *
 * @param projectJars             Project jars loaded in for the analysis
 * @param libraryJars             Library jars loaded in for the analysis
 * @param tplJar                  The library jar file from which the dummy should have been created.
 *                                (from start to end, including call graph generation)
 * @param includeNonPublicMethods Configuration flag whether the dummy only contains public methods (`true`) or also
 *                                all other methods (e.g. indirectly accessed private methods)
 * @param callGraphAlgorithmName  Call graph algorithm used for the analysis
 * @param entryPointsFinder       What entry points finder has been used for the analysis
 * @param customEntryPoints       What additional entry points have been set
 * @param tplDummyOutputPath      Path to where the TPL dummy has been written to
 * @param writtenFiles            List of all classes that have been used by the project.
 *                                Includes the number of used methods.
 */
case class JsonReport
(
  projectJars: Array[String],
  libraryJars: Array[String],
  tplJar: String,
  includeNonPublicMethods: Boolean,
  callGraphAlgorithmName: String,
  entryPointsFinder: String,
  customEntryPoints: List[SelectedMethodsOfClass],
  tplDummyOutputPath: String,
  writtenFiles: List[WrittenClassFile]
)

object JsonReport {
  implicit val format: OFormat[JsonReport] = Json.format[JsonReport]
}
