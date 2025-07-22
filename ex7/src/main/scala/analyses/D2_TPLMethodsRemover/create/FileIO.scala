package analyses.D2_TPLMethodsRemover.create

import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig
import org.opalj.ba.toDA
import org.opalj.bc.Assembler
import org.opalj.br.ClassFile
import org.slf4j.MarkerFactory
import play.api.libs.json.Json

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path}

/**
 * Helper object for JSON input/output operations.
 * Handles config and result file read/write as JSON.
 */
object FileIO {

  /**
   * Writes the created modified class files to the given output path.
   *
   * @param path               Path where to output the dummy (must be a folder!)
   * @param modifiedClassFiles The modified class files created by the analysis.
   */
  def writeModifiedClassFiles(logger: Logger, path: String, modifiedClassFiles: Iterable[ClassFile]): Unit = {
    val outputPath = Path.of(path)
    var replacedInvalidCharacter = false
    logger.info(s"Writing created class files to path $outputPath...")
    modifiedClassFiles.foreach { modifiedClassFile =>
      val usedMethods = modifiedClassFile.methods.length
      val sampleMethod = modifiedClassFile.methods(scala.util.Random.nextInt(usedMethods))
      logger.info(
        s"""Class file ${modifiedClassFile.fqn.replace('/', '.')}:
           |  - Used methods: $usedMethods
           |  - Sample method: ${sampleMethod.signatureToJava(true)}""".stripMargin
      )

      val newClassBytes: Array[Byte] = Assembler(toDA(modifiedClassFile))

      // Windows does not accept some characters that may be contained in the class file names
      // Thus, replace them with similar-looking characters that are allowed
      val sanitizedClassFileName = modifiedClassFile.fqn.map { c =>
        c match {
          case ':' =>
            replacedInvalidCharacter = true
            'ː' // Unicode character U+02D0
          case '<' =>
            replacedInvalidCharacter = true
            '‹' // Unicode character U+2039
          case '>' =>
            replacedInvalidCharacter = true
            '›' // Unicode character U+203A
          case other => other
        }
      }

      val classFilePath = Path.of(s"$outputPath/$sanitizedClassFileName.class")

      Files.createDirectories(classFilePath.getParent)
      Files.write(classFilePath, newClassBytes)
    }

    if (replacedInvalidCharacter) {
      logger.info(
        MarkerFactory.getMarker("BLUE"),
        "Note: At least one of the class files contained a character not allowed in Windows file names (':', '<' or '>')."
      )
      logger.info(
        MarkerFactory.getMarker("BLUE"),
        "      Such characters have been replaced with similar-looking Unicode characters (U+02D0, U+2039 or U+203A).\n"
      )
    }
  }

  /**
   * Writes a JSON report for the TPLMethodsRemover analysis.
   *
   * @param writtenClassFiles The ClassFiles written
   * @param config Config for this program
   * @param tplJar Path to the jar from which the dummy has been created
   * @param dummyPath Path where the TPL dummy has been written to
   * @param outputPath Path where this report should be written to
   */
  def writeJsonReport
  (
    writtenClassFiles: List[ClassFile],
    config: StaticAnalysisConfig,
    tplJar: String,
    dummyPath: String,
    outputPath: String
  ): Unit = {
    val analysisConfig = config.tplMethodsRemover
    val report = JsonReport(
      projectJars = config.projectJars.map(file => file.getPath.replace('\\', '/')),
      libraryJars = config.libraryJars.map(file => file.getPath.replace('\\', '/')),
      tplJar = tplJar,
      includeNonPublicMethods = analysisConfig.includeNonPublicMethods,
      callGraphAlgorithmName = analysisConfig.callGraphAlgorithmName.toUpperCase,
      entryPointsFinder = analysisConfig.entryPointsFinder,
      customEntryPoints = analysisConfig.customEntryPoints,
      tplDummyOutputPath = dummyPath,
      writtenFiles = writtenClassFiles.map { classFile =>
        WrittenClassFile(classFile.thisType.toJava, classFile.methods.length)
      }
    )

    val writer = new PrintWriter(new File(outputPath))
    writer.write(Json.prettyPrint(Json.toJson(report)))
    writer.close()
  }
}