package modify.data

import org.opalj.tac.cg.CallGraphKey

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
 * @param entryPointsFinder Combination of the EntryPointsFinder and InstantiatedTypesFinder to use for the project.
 *                          First string contains the EntryPointsFinder, second one the InstantiatedTypesFinder. In the
 *                          config json, you have 4 different options to choose from that set this tuple accordingly:
 *                          "custom", "application", "applicationwithjre" and "library".
 * @param customEntryPoints Additional entry points the user can enter for the call graph of the project.
 * @param callGraphAlgorithm Call graph algorithm to use. User can decide in config between
 *                           "cha", "rta", "xta" and "1-1-cfa".
 * @param outputJson Optional output file name for results. If not given, no output file is generated.
 */
case class AnalysisConfig(
  projectJars: List[File],
  libraryJars: List[File] = List.empty,
  completelyLoadLibraries: Boolean = false,
  criticalMethods: List[SelectedMethodsOfClass] = List.empty,
  ignoreCalls: List[IgnoredCall] = List.empty,
  entryPointsFinder: (String, String),
  customEntryPoints: List[SelectedMethodsOfClass] = List.empty,
  callGraphAlgorithm: CallGraphKey,
  outputJson: Option[String] = None,
)
