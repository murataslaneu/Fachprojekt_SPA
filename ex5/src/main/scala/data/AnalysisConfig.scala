package data

import java.io.File

/**
 * Data model for reading the JSON config file.
 * Holds all analysis parameters.
 *
 * @param projectJars Jar file(s) of the project to be analyzed
 * @param libraryJars Used library jar files of the project (can be any file the project uses, direct dependencies,
 *                    transitive dependencies, Java standard library rt.jar, ...)
 * @param completelyLoadLibraries Optional boolean whether OPAL should load the libraries completely (`true`) or only
 *                                use as interfaces (`false`)
 * @param interactive Required boolean whether the selecting the domain is interactive or not
 * @param showResults Optional boolean whether automatically the DeadCodeReportViewer should be called.
 *                    Defaults to false.
 */
case class AnalysisConfig(
                           projectJars: List[File],
                           libraryJars: List[File],
                           completelyLoadLibraries: Boolean,
                           interactive: Boolean,
                           showResults: Boolean
                         )
