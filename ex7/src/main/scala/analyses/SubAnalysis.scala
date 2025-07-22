package analyses

import com.typesafe.scalalogging.Logger
import configs.StaticAnalysisConfig

/**
 * A sub-analysis is a single analysis executed inside this analysis suite.
 */
trait SubAnalysis {

  /** Logger used in the sub-analysis */
  val logger: Logger

  /** The name of the sub-analysis */
  val analysisName: String

  /** The number of the sub-analysis */
  val analysisNumber: String

  /**
   * Name of the folder (inside the resultsOutputPath of [[StaticAnalysisConfig]])
   * where the sub-analysis will put their results.
   */
  val outputFolderName: String

  /**
   * Whether this sub-analysis should actually be executed or not.
   *
   * Mainly exists to make the process of executing each analysis easier.
   */
  val shouldExecute: Boolean

  /** Function that executes the sub-analysis with the given config */
  def executeAnalysis(config: StaticAnalysisConfig): Unit

}