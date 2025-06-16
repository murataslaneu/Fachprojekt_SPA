package modify.data

import java.io.File

/**
 * Data model for reading the JSON config file.
 * Holds all analysis parameters.
 *
 * @param projectJars Required files to the main project jar files to be analyzed.
 * @param libraryJars Optional list of used libraries in the project. Include them to make the call graph more complete.
 *                    Is empty list if not used.
 * @param completelyLoadLibraries Flag that indicates whether the given libraries should be loaded completely. If not
 *                                given, default to false.
 * @param criticalMethods List of selected methods for each entered class that should be treated as critical.
 * @param ignoreCalls List of method calls in a method of a class to ignore/suppress. These calls will not be replaced
 *                    if found, even if the method call is critical.
 * @param outputClassFiles FOLDER where the modified class files will be written to.
 * @param outputJson Optional output file name for results. If not given, no output file is generated.
 */
case class AnalysisConfig(
  projectJars: List[File],
  libraryJars: List[File],
  completelyLoadLibraries: Boolean,
  criticalMethods: List[SelectedMethodsOfClass],
  ignoreCalls: List[IgnoredCall],
  outputClassFiles: String,
  outputJson: Option[String]
)
