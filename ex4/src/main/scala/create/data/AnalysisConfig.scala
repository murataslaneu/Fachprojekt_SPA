package create.data

import org.opalj.tac.cg.CallGraphKey

import java.io.File

/**
 * Data model for reading the JSON config file.
 * Holds all analysis parameters.
 *
 * @param projectJars Required files to the main project jar files to be analyzed.
 * @param libraryJars List of some/all third party libraries (can be direct dependencies or transitive ones) of the project.
 * @param tplJar Required path to the jar of the third party library of which the class files should be created from.
 * @param includeNonPublicMethods Flag whether non-public methods should also be included in the class files,
 *                                which may be reachable via indirect calls. Defaults to true.
 * @param entryPointsFinder Combination of the EntryPointsFinder and InstantiatedTypesFinder to use for the project.
 *                          First string contains the EntryPointsFinder, second one the InstantiatedTypesFinder. In the
 *                          config json, you have 4 different options to choose from that set this tuple accordingly:
 *                          "custom", "application", "applicationwithjre" and "library". Defaults to "application".
 * @param customEntryPoints Additional entry points the user can enter for the call graph of the project.
 * @param callGraphAlgorithm Call graph algorithm to use. User can decide in config between
 *                           "cha", "rta", "xta" and "1-1-cfa".
 * @param outputClassFiles FOLDER where the created class files will be written to.
 */
case class AnalysisConfig(
  projectJars: List[File],
  libraryJars: List[File],
  tplJar: File,
  includeNonPublicMethods: Boolean,
  entryPointsFinder: (String, String),
  customEntryPoints: List[SelectedMethodsOfClass],
  callGraphAlgorithm: CallGraphKey,
  outputClassFiles: String
)
