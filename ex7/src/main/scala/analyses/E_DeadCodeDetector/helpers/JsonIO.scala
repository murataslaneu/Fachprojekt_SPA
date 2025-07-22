package analyses.E_DeadCodeDetector.helpers

import analyses.E_DeadCodeDetector.data.{AnalysisConfig, DeadCodeReport, MultiDomainDeadCodeReport}
import play.api.libs.json._

import java.io.{File, PrintWriter}

/**
 * Helper object for JSON input/output operations.
 * Handles config and result file read/write as JSON.
 */
object JsonIO {
  /**
   * Writes generated report in json format.
   *
   * @param result The DeadCodeReport to write
   * @param path Path where the report should be written to
   */
  def writeSingleDomainResult(result: DeadCodeReport, path: String): Unit = {
    val json = Json.prettyPrint(Json.toJson(result))
    val writer = new PrintWriter(new File(path))
    writer.write(json)
    writer.close()
  }

  /**
   * Writes generated report in json format.
   *
   * @param result The DeadCodeReport to write
   * @param path Path where the report should be written to
   */
  def writeMultiDomainResult(result: MultiDomainDeadCodeReport, path: String): Unit = {
    val json = Json.prettyPrint(Json.toJson(result))
    val writer = new PrintWriter(new File(path))
    writer.write(json)
    writer.close()
  }
}