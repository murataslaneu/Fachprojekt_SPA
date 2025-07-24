package analyses.D1_CriticalMethodsRemover.modify

import data.{AnalysisResult, OriginalBytecode}
import play.api.libs.json.Json

import java.io.{File, PrintWriter}

/**
 * Helper object for JSON input/output operations.
 * Handles config and result file read/write as JSON.
 */
object FileIO {
  /**
   * Writes a list of analysis results to a JSON file
   */
  def writeResult(result: List[AnalysisResult], path: String): Unit = {
    val json = Json.prettyPrint(Json.toJson(result))
    val writer = new PrintWriter(new File(path))
    writer.write(json)
    writer.close()
  }

  /**
   *  Helper method for reading JSON result files in tests
   */
  def readJsonResult(path: String): List[AnalysisResult] = {
    val source = scala.io.Source.fromFile(path, "UTF-8")
    val content = try source.mkString finally source.close()
    val parsed = Json.parse(content).validate[List[AnalysisResult]]
    parsed.getOrElse(throw new IllegalArgumentException(s"Invalid JSON in $path"))
  }

  /**
   * Writes a text file that contains all the original bytecode of each modified method (to avoid spamming the logs).
   *
   * @param originalBytecodes Bytecode to write.
   * @param path Path to write to.
   */
  def writeOriginalBytecodeFile(originalBytecodes: List[OriginalBytecode], path: String): Unit = {
    val fileStringBuilder = new StringBuilder()
    originalBytecodes.foreach { originalBytecode =>
      fileStringBuilder.append(s"Class: ${originalBytecode.className} (from ${originalBytecode.fromJar}\n")
      fileStringBuilder.append(s"Method: ${originalBytecode.method}\n")
      fileStringBuilder.append(s"Bytecode:\n")
      fileStringBuilder.append(originalBytecode.bytecode)
      fileStringBuilder.append("\n==================================================\n\n")

    }
    val writer = new PrintWriter(new File(path))
    writer.write(fileStringBuilder.toString)
    writer.close()
  }
}