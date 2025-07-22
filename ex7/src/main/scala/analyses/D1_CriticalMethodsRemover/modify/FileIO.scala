package analyses.D1_CriticalMethodsRemover.modify

import data.AnalysisResult
import play.api.libs.json._

import java.io.{File, PrintWriter}

/**
 * Helper object for JSON input/output operations.
 * Handles config and result file read/write as JSON.
 */
object FileIO {
  // Writes a list of analysis results to a JSON file
  def writeResult(result: List[AnalysisResult], path: String): Unit = {
    val json = Json.prettyPrint(Json.toJson(result))
    val writer = new PrintWriter(new File(path))
    writer.write(json)
    writer.close()
  }

  // Helper method for reading JSON result files in tests
  def readJsonResult(path: String): List[AnalysisResult] = {
    val source = scala.io.Source.fromFile(path, "UTF-8")
    val content = try source.mkString finally source.close()
    val parsed = Json.parse(content).validate[List[AnalysisResult]]
    parsed.getOrElse(throw new IllegalArgumentException(s"Invalid JSON in $path"))
  }
}