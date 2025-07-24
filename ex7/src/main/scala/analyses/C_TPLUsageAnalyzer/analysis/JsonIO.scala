package analyses.C_TPLUsageAnalyzer.analysis

import java.io.{File, PrintWriter}
import play.api.libs.json._

/**
 * Helper object for JSON input/output operations.
 * Handles result file write as JSON.
 */
object JsonIO {
  /** Writes the analysis result to a file in JSON format */
  def writeResult(result: TPLAnalysisResult, path: String): Unit = {
    val writer = new PrintWriter(new File(path))
    writer.write(Json.prettyPrint(Json.toJson(result)))
    writer.close()
  }
}