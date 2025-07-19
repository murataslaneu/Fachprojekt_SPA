package util

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import org.opalj.br.analyses.{Project, SomeProject}
import org.opalj.br.{ClassFile, reader}
import org.opalj.br.reader.Java9LibraryFramework
import org.opalj.log.{GlobalLogContext, LogContext}
import org.opalj.log.OPALLogger.{error, info}

import java.io.File
import java.net.URL

/**
 * This object is responsible for creating the project files used for each sub-analysis.
 *
 * Contained code is basically a copy from the code of org.opalj.br.analyses.AnalysisApplication
 */
object ProjectInitializer {

  implicit val logContext: LogContext = GlobalLogContext

  /**
   * Creates a OPAL project usable for analysis.
   *
   * @param cpFiles                 The project class files to analyze.
   * @param libcpFiles              The library class files to analyze.
   * @param completelyLoadLibraries Boolean whether the library class files should be loaded completely (`true`) of just
   *                                as interfaces (`false`).
   * @param configuredConfig        The config to use for OPAL. Used e.g. to configure the entry points for a call graph.
   * @return A OPAL project ready for analysis.
   */
  def setupProject(
                    logger: Logger,
                    cpFiles: Array[File],
                    libcpFiles: Array[File],
                    completelyLoadLibraries: Boolean = false,
                    configuredConfig: Config = ConfigFactory.load
                  ): Project[URL] = {
    info("Creating OPAL project", "Reading project class files")

    // OPALs class file reader taken from the Project companion object.
    // Does... something with the project and library files.
    // There is basically no documentation on what it does in the OPAL ScalaDoc.
    val JavaClassFileReader = Project.JavaClassFileReader(logContext, configuredConfig)

    // Get project class files
    val (classFiles, exceptions1) =
      reader.readClassFiles(
        cpFiles,
        JavaClassFileReader.ClassFiles,
        file => info("Creating OPAL project", "    File: " + file)
      )

    // Get library class files
    val (libraryClassFiles, exceptions2) = {
      if (libcpFiles.nonEmpty) {
        info("Creating OPAL project", "Reading library class files")
        reader.readClassFiles(
          libcpFiles,
          if (completelyLoadLibraries) {
            JavaClassFileReader.ClassFiles
          } else {
            Java9LibraryFramework.ClassFiles
          },
          file => info("Creating OPAL project", "    file: " + file)
        )
      } else {
        (Iterable.empty[(ClassFile, URL)], List.empty[Throwable])
      }
    }

    // Build project out of the class files and config
    val project =
      Project(
        projectClassFilesWithSources = classFiles,
        libraryClassFilesWithSources = libraryClassFiles,
        libraryClassFilesAreInterfacesOnly = !completelyLoadLibraries,
        virtualClassFiles = Iterable.empty
      )(config = configuredConfig)

    // Handle exceptions that were occurring while reading the project and library class files
    handleParsingExceptions(project, logger, exceptions1 ++ exceptions2)

    // Prints out the project statistics via the OPAL logger
    //    val statistics =
    //      project
    //        .statistics.map(kv => "- " + kv._1 + ": " + kv._2)
    //        .toList.sorted.reverse
    //        .mkString("project statistics:\n\t", "\n\t", "\n")
    //    info("OPAL project", statistics)(project.logContext)

    // Return the created project
    project
  }

  /**
   * Prints out the errors that were occurring during the creation of the project via the OPAL logger.
   */
  private def handleParsingExceptions(project: SomeProject, logger: Logger, exceptions: Iterable[Throwable]): Unit = {
    if (exceptions.isEmpty) return

    implicit val logContext: LogContext = project.logContext
    for (exception <- exceptions) {
      error("Creating OPAL project", "Ignoring invalid class file", exception)
    }
    if (exceptions.nonEmpty) {
      logger.warn(s"${exceptions.size} errors occurred while setting up the OPAL project.")
    }
  }

  /**
   * Retrieves the statistics of a project and turns them into a string.
   */
  def projectStatistics(project: Project[_]): String = {
    project
      .statistics.map(kv => "- " + kv._1 + ": " + kv._2)
      .toList.sorted.reverse
      .mkString("OPAL project statistics:\n    ", "\n    ", "")
  }

}
