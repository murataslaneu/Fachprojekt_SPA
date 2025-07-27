package analyses.D2_TPLMethodsRemover.create

import configs.StaticAnalysisConfig
import org.opalj.ba.toDA
import org.opalj.bc.Assembler
import org.opalj.br.ClassFile
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
   * @return Whether at least one invalid character has been replaced by a similar-looking character
   */
  def writeModifiedClassFiles(path: String, modifiedClassFiles: Iterable[ClassFile]): Boolean = {
    val outputPath = Path.of(path)
    var replacedInvalidCharacter = false

    modifiedClassFiles.foreach { modifiedClassFile =>

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

    replacedInvalidCharacter
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