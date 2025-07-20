package util

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import data.SelectedMethodsOfClass
import org.opalj.br.analyses.{Project, SomeProject}
import org.opalj.br.{ClassFile, reader}
import org.opalj.br.reader.Java9LibraryFramework
import org.opalj.log.{GlobalLogContext, LogContext}
import org.opalj.log.OPALLogger.{error, info}

import java.io.File
import java.net.URL
import scala.collection.mutable
import scala.jdk.CollectionConverters.{IterableHasAsJava, MapHasAsJava}

/**
 * This object is responsible for creating the project files used for each sub-analysis.
 *
 * Contained code is mostly a copy from the code of org.opalj.br.analyses.AnalysisApplication,
 * with [[setupOPALProjectConfig]] being the only somewhat custom part.
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

  /**
   * Retrieves the corresponding OPAL config from the set entryPointsFinder and customEntryPoints in our analysis config.
   *
   * @param entryPointsFinder String "custom", "application", "applicationwithjre" or "library". Anything else will
   *                          throw an [[IllegalArgumentException]].
   * @param customEntryPoints List of methods (grouped by class) that should be used by OPAL as additional entry points
   *                          for the call graph generation.
   * @return The configured config that can be used by OPAL when setting up a project.
   */
  def setupOPALProjectConfig(entryPointsFinder: String, customEntryPoints: List[SelectedMethodsOfClass]): Config = {
    val entryPointsFinderValue = entryPointsFinder match {
      case "custom" => ("org.opalj.br.analyses.cg.ConfigurationEntryPointsFinder",
        "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder")
      case "application" => ("org.opalj.br.analyses.cg.ApplicationWithoutJREEntryPointsFinder",
        "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder")
      case "applicationwithjre" => ("org.opalj.br.analyses.cg.ApplicationEntryPointsFinder",
        "org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder")
      case "library" => ("org.opalj.br.analyses.cg.LibraryEntryPointsFinder",
        "org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
      case invalid => throw new IllegalArgumentException(s"Invalid entry points finder $invalid selected.")
    }

    val overridesMap: mutable.Map[String, Object] = mutable.Map(
      "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis" -> entryPointsFinderValue._1,
      "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis" -> entryPointsFinderValue._2
    )

    if (customEntryPoints.nonEmpty) {
      val customEntryPointsValue = customEntryPoints.flatMap { eps =>
        eps.methods.map { epMethod =>
          Map("declaringClass" -> eps.className, "name" -> epMethod).asJava
        }
      }.asJava
      overridesMap.put("org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints", customEntryPointsValue)
    }

    ConfigFactory.parseMap(overridesMap.asJava).withFallback(ConfigFactory.load).resolve()
  }
}
