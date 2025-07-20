package util

import data.SelectedMethodsOfClass

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
}
