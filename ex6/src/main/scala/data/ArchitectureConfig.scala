package data

/**
 * Configuration for architecture validation analysis
 */
case class ArchitectureConfig(
                               projectJars: List[java.io.File],
                               libraryJars: List[java.io.File],
                               specificationFile: String,
                               outputJson: String,
                               completelyLoadLibraries: Boolean = false
                             )
