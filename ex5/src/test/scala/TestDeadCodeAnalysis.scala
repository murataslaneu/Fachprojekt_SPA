import com.typesafe.config.ConfigFactory
import data.{AnalysisConfig, DeadCodeReport}
import org.opalj.br.analyses.ProgressManagement
import org.opalj.log.GlobalLogContext
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.{JsSuccess, Json}

import java.io.File
import java.nio.file.{Files, Path}
import scala.io.Source

class TestDeadCodeAnalysis extends AnyFunSuite {
  private val config: AnalysisConfig = AnalysisConfig(
    projectJars = List(new File("src/test/testFiles/MinimalExample.jar")),
    libraryJars = List.empty[File],
    completelyLoadLibraries = false,
    interactive = false,
    outputJson = "src/test/testFiles/testResults.json"
  )

  test("Analysis should execute with expected results") {
    // Check if results file exists
    val path = Path.of("src/test/testFiles/testResults.json")
    Files.deleteIfExists(path)

    // Execute analysis
    DeadCodeDetector.config = Some(config)
    DeadCodeDetector.configSet = true
    val project = DeadCodeDetector.setupProject(List(), List(), completelyLoadLibraries = false, ConfigFactory.load)(GlobalLogContext)
    DeadCodeDetector.analyze(project, Seq(), ProgressManagement.None)

    // Test if file has been written at the expected location
    assert(Files.exists(path))

    // Try reading file and formatting it into a DeadCodeReport
    val fileSource = Source.fromFile("src/test/testFiles/testResults.json")
    val jsonContents = try Json.parse(fileSource.getLines().mkString) finally fileSource.close()
    val reportResult = jsonContents.validate[DeadCodeReport]
    assert(reportResult.isInstanceOf[JsSuccess[DeadCodeReport]])
    val report = reportResult.get
    // Read contents and check if they match the expected result
    // Check report
    assert(report.filesAnalyzed == List("src/test/testFiles/MinimalExample.jar"))
    assert(report.domainUsed.startsWith("org.opalj"))
    assert(report.totalRuntimeMs >= 0)
    assert(report.methodsFound.length == 1)
    // Check (only) method
    val method = report.methodsFound.head
    assert(method.fullSignature == "void main(java.lang.String[])")
    assert(method.enclosingTypeName == "com.example.Main")
    assert(method.numberOfTotalInstructions == 13)
    assert(method.numberOfDeadInstructions == method.deadInstructions.length)
    assert(method.deadInstructions.nonEmpty)
    // Check if instructions (probably) have expected format
    assert(method.deadInstructions.forall(instruction => instruction.programCounter >= 0))
    assert(method.deadInstructions.forall(instruction => instruction.stringRepresentation.nonEmpty))
  }
}
