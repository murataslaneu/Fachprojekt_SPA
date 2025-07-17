package analyses

import configs.StaticAnalysisConfig

trait SubAnalysis {
  def executeAnalysis(config: StaticAnalysisConfig): Unit
}
