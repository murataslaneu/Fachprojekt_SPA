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
    val source = scala.io.Source.fromFile(path)
    try {
      val json = Json.parse(source.mkString)

      val projectJars = {
        val result = json \ "projectJars"
        if (result.isDefined) result.get.as[List[String]]
        else throw new NoSuchElementException(s"Project jar(s) missing in $path")
      }
      projectJars.foreach {path => path.replace('\\', '/')}
      val tplJars = {
        val result = json \ "tplJars"
        if (result.isDefined) result.get.as[List[String]]
        else List.empty[String]
      }
      tplJars.foreach {path => path.replace('\\', '/')}
      val callGraphAlgorithm = {
        val result = json \ "callGraphAlgorithm"
        if (result.isDefined) result.get.as[String]
        else "RTA"
      }
      val outputJson = {
        val result = json \ "outputJson"
        if (result.isDefined) Some(result.get.as[String])
        else None
      }
      val isLibraryProject = {
        val result = json \ "isLibraryProject"
        if (result.isDefined) result.get.as[Boolean]
        else false
      }
      val countAllMethods = {
        val result = json \ "countAllMethods"
        if (result.isDefined) result.get.as[Boolean]
        else false
      }
      AnalysisConfig(projectJars, tplJars, callGraphAlgorithm, outputJson, isLibraryProject, countAllMethods)
    }
    finally {
      source.close()
    }
  }

  /** Writes the analysis result to a file in JSON format */
  def writeResult(result: TPLAnalysisResult, path: String): Unit = {
    val writer = new PrintWriter(new File(path))
    writer.write(Json.prettyPrint(Json.toJson(result)))
    writer.close()
  }
}