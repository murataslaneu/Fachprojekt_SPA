package analyses

import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig

trait SubAnalysis {
  val logger: Logger

  def executeAnalysis(config: StaticAnalysisConfig): Unit
}
