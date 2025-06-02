package visualization

import org.knowm.xchart.{CategoryChart, CategoryChartBuilder, SwingWrapper}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

/**
 * Utility object for visualizing the API coverage results of third-party libraries (TPL)
 * after static analysis. Reads the analysis output (JSON), prints a summary to the console,
 * and displays a bar chart of method coverage for each library.
 */
object TPLUsageVisualizer {
  /**
   * Displays a chart of TPL coverage using the analysis result JSON file.
   *
   * @param resultFile Path to the JSON result file (default: "result.json")
   */
  def showChart(resultFile: String = "result.json"): Unit = {
    // Read the JSON result file as a string
    val jsonStr = new String(Files.readAllBytes(Paths.get(resultFile)), StandardCharsets.UTF_8)
    // Parse the JSON using ujson
    val data = ujson.read(jsonStr)
    val analysisArr = data("analysis").arr

    // Extract library names, usage percentages, and method counts from the JSON array
    val libraries = analysisArr.map(obj => obj("library").str).toList
    val usagePercents: java.util.List[java.lang.Double] =
      analysisArr.map(obj => obj("usageRatio").num * 100).map(java.lang.Double.valueOf).asJava
    val usedMethods = analysisArr.map(obj => obj("usedMethods").num.toInt)
    val totalMethods = analysisArr.map(obj => obj("totalMethods").num.toInt)

    // Print summary information for each library to the console
    libraries.zipWithIndex.foreach { case (lib, idx) =>
      println(f"$lib: ${usedMethods(idx)}/${totalMethods(idx)} used (${usagePercents.get(idx)}%.2f%% coverage)")
    }

    // Create a bar chart to visualize the coverage for each library
    val chart: CategoryChart = new CategoryChartBuilder()
      .width(900)
      .height(500)
      .title("TPL API Coverage")
      .xAxisTitle("Library")
      .yAxisTitle("Coverage (%)")
      .build()

    // Add the coverage series to the chart
    chart.addSeries("Coverage", libraries.asJava, usagePercents)

    // Display the chart in a Swing window
    new SwingWrapper(chart).displayChart()
  }
}
