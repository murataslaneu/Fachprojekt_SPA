package analysis

import java.io.{File, PrintWriter}
import play.api.libs.json._

/**
 * Helper object for JSON input/output operations.
 * Handles config and result file read/write as JSON.
 */
object JsonIO {

  /** Reads a JSON config file and returns the AnalysisConfig object */
  def readConfig(path: String): AnalysisConfig = {
    val jsonStr = scala.io.Source.fromFile(path).mkString
    Json.parse(jsonStr).as[AnalysisConfig]
  }

  /** Writes the analysis result to a file in JSON format */
  def writeResult(result: TPLAnalysisResult, path: String): Unit = {
    val writer = new PrintWriter(new File(path))
    writer.write(Json.prettyPrint(Json.toJson(result)))
    writer.close()
  }

  /** Converts the analysis result to a pretty-printed JSON string (for console output) */
  def toJsonString(result: TPLAnalysisResult): String =
    Json.prettyPrint(Json.toJson(result))
}