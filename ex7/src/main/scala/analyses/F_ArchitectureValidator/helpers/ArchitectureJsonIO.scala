package analyses.F_ArchitectureValidator.helpers

import analyses.F_ArchitectureValidator.data.{ArchitectureReport, ArchitectureSpec}
import play.api.libs.json._

import java.io.{File, PrintWriter}
import scala.io.Source

object ArchitectureJsonIO {
  /**
   * Writes architecture report to JSON file
   */
  def writeReport(report: ArchitectureReport, path: String): Unit = {
    val json = Json.prettyPrint(Json.toJson(report))
    val writer = new PrintWriter(new File(path))
    writer.write(json)
    writer.close()
  }

  /**
   * Reads architecture specification from JSON file
   */
  def readSpecification(specFile: String): ArchitectureSpec = {
    val source = Source.fromFile(specFile)
    val json = try Json.parse(source.mkString) finally source.close()
    json.as[ArchitectureSpec]
  }
}