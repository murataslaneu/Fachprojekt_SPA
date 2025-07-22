package util

import data.SelectedMethodsOfClass

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import scala.util.Random

/**
 * Object holding some frequently used functions throughout the analyses.
 *
 * Just a random composition of functions.
 */
object Utils {

  /**
   * Builds a string from an Iterable of SelectedMethodsOfClass that can be used to show as a configuration.
   * The samples will also be sorted lexicographically.
   *
   * @param selected The Iterable of SelectedMethodsOfClass to build the string from.
   * @param k        The number of sample classes to take.
   * @param l        The number of sample methods to take per class.
   * @return String that can be used e.g. to show as a configuration.
   */
  def buildSampleSelectedMethodsString(selected: Iterable[SelectedMethodsOfClass], k: Int, l: Int): String = {
    val samples = Random.shuffle(selected).take(k).toList
    if (samples.isEmpty) return "None"
    val mainString = samples.map { sample =>
      val className = sample.className
      val selectedMethods = sample.methods.take(l).mkString("\n      - ", "\n      - ", "")
      val moreMethods = if (sample.methods.length > l) s"\n... and ${sample.methods.length - l} more methods"
      else ""
      s"$className:$selectedMethods$moreMethods"
    }.sorted.mkString("\n    - ", "\n    - ", "")
    val moreClasses = if (selected.size > k) s"\n... and ${selected.size - k} more classes"
    else ""

    s"$mainString$moreClasses"
  }

  /**
   * Initializes the output path for a sub-analysis.
   *
   * May create the directory and/or delete the file(s) at/in the path.
   *
   * @param path The path to initialize.
   * @return `true` if at least one file was deleted, `false` otherwise.
   */
  def initializeSubAnalysisOutputDirectory(path: String): Boolean = {
    val outputPath = Path.of(path)
    var deletedFile = false
    if (Files.isRegularFile(outputPath)) {
      // Regular file exists that has the same name as the directory that should be created
      // File must be deleted
      Files.delete(outputPath)
      deletedFile = true
    }
    if (Files.notExists(outputPath)) {
      // Directory doesn't exist yet, create it
      Files.createDirectory(outputPath)
    }
    else {
      // Directory already exists
      // Might contain files, in this case delete them
      Files.walkFileTree(outputPath, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          deletedFile = true
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          // Result folder itself should not be deleted!
          if (dir != outputPath) {
            Files.delete(dir)
            deletedFile = true
          }
          FileVisitResult.CONTINUE
        }
      })
    }
    deletedFile
  }
}
