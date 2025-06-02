import org.knowm.xchart.{CategoryChart, CategoryChartBuilder, SwingWrapper}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

object TPLUsageVisualizer {
  def main(args: Array[String]): Unit = {
    // Read JSON file
    val jsonStr = new String(Files.readAllBytes(Paths.get("result.json")), StandardCharsets.UTF_8)
    val data = ujson.read(jsonStr)
    val analysisArr = data("analysis").arr

    // Extract library names and usage percentages
    val libraries = analysisArr.map(obj => obj("library").str).toList
    val usagePercents: java.util.List[java.lang.Double] =
      analysisArr.map(obj => obj("usageRatio").num * 100).map(java.lang.Double.valueOf).asJava
    val usedMethods = analysisArr.map(obj => obj("usedMethods").num.toInt)
    val totalMethods = analysisArr.map(obj => obj("totalMethods").num.toInt)

    // Print info to console
    libraries.zipWithIndex.foreach { case (lib, idx) =>
      println(f"$lib: ${usedMethods(idx)}/${totalMethods(idx)} used (${usagePercents.get(idx)}%.2f%% coverage)")
    }

    // Create chart
    val chart: CategoryChart = new CategoryChartBuilder()
      .width(900)
      .height(500)
      .title("TPL API Coverage")
      .xAxisTitle("Library")
      .yAxisTitle("Coverage (%)")
      .build()

    chart.addSeries("Coverage", libraries.asJava, usagePercents)

    // Show chart window
    new SwingWrapper(chart).displayChart()
  }
}
