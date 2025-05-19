package misc

import java.io.File
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.util.control.Breaks.{break, breakable}

/**
 * This class should be responsible for every file I/O, like reading text files or writing something to a file.
 */
object FileIO {

  /**
   * Checks if the given file path is a readable txt file.
   *
   * @param path The file path to check
   * */
  def fileReadable(path: String): Boolean = {
    val file = new File(path)
    file.exists() && file.isFile && path.endsWith(".txt")
  }


/* Example of a valid text file to read the critical methods from:

java.lang.System:
  getSecurityManager
  setSecurityManager

# Previous empty line is important. Multiple lines are also allowed.
# Note that a comment does not count as an empty line!
# Following empty line is not needed, but allowed.

Main:
  someCriticalMethod

# End of file.
*/
  /**
   * Reads a text file and creates from its content a list of CriticalClassMethods. Checking if the file exist should be
   * done outside the function.
   *
   * Syntax:
   *
   * - A "//" at the start of the line denotes a comment in the file. It will be ignored.
   *     Example: `// This is a comment line.`
   *
   * - You first start with the fully qualified name of a class where you have a critical method the detector should
   *     look out for, followed by a colon. Example: `java.lang.System:`
   *
   * - After that on the next lines, you add the method names of the class that should be seen as critical.
   *     The parameter list of the method should be omitted. Example line: `getSecurityManager`
   *
   * - When you added all methods of the class, you must leave the next line empty. This way, you tell that you added
   *     all methods for the class.
   *
   * - Following the empty line, you may add more classes and their corresponding methods as you like.
   *
   * - Note that you may indent all lines as you like to improve readability, as it has no effect.
   *
   * @param filePath Path to the file
   *
   */
   def readIncludeMethodsFile(filePath: String): ListBuffer[CriticalClassMethods] = {


    val criticalMethods = ListBuffer[CriticalClassMethods]()

    // Reading file
    val criticalMethodsOfClass: ListBuffer[String] = ListBuffer()

    val source = Source.fromFile(filePath)
    var currentClass = ""
    try {
      for (line <- source.getLines()) {
        breakable {
          val currentLine = line.strip()

          // Comments are ignored
          if (currentLine.startsWith("//")) break

          // currentClass empty --> Next line may contain class name.
          else if (currentClass.isEmpty) {
            if (currentLine.endsWith(":")) currentClass = currentLine.dropRight(1).strip()
            else currentClass = currentLine
          }

          // Line empty, but currentClass is not --> Finished with the current class, add to criticalMethods
          else if (currentLine.isEmpty) {
            // If no methods have been added for the class for some reason, it does not need to be added
            if (criticalMethodsOfClass.nonEmpty) {
              criticalMethods.addOne(CriticalClassMethods(currentClass, criticalMethodsOfClass.toList))
            }
            criticalMethodsOfClass.clear()
            currentClass = ""
          }

          // Else: Some method name, add to criticalMethodsOfClass
          else {
            criticalMethodsOfClass.addOne(currentLine)
          }
        }
      }
    }
    finally {
      source.close()
    }

    // Loop finished, add current class if there was no empty line at the end of the file.
    if (currentClass.nonEmpty && criticalMethodsOfClass.nonEmpty) {
      criticalMethods.addOne(
        CriticalClassMethods(currentClass, criticalMethodsOfClass.toList)
      )
    }

    criticalMethods
  }

  /**
   * Reads a suppression configuration file and parses a list of suppressed method calls.
   *
   * Each line in the file should define a suppressed call in the following format:
   *   callerClass#callerMethod -> targetClass#targetMethod
   * Lines starting with '//' are treated as comments and ignored.
   *
   * Example:
   *   com.example.MyClass#main -> java.lang.System#setSecurityManager
   *
   * @param filePath The path to the suppression configuration file.
   * @return A list of SuppressedCall instances representing suppressed call pairs.
   */
  def readSuppressCallsFile(filePath: String): ListBuffer[SuppressedCall] = {
    val suppressedCalls = ListBuffer[SuppressedCall]()

    // Read the file line by line
    val source = Source.fromFile(filePath)
    try {
      for (line <- source.getLines()) {
        val trimmedLine = line.strip()

        // Ignore empty lines and comment lines
        if (!trimmedLine.startsWith("//") && trimmedLine.nonEmpty) {

          // Expected format: callerClass#callerMethod -> targetClass#targetMethod
          val parts = trimmedLine.split("->")
          if (parts.length == 2) {
            val callerParts = parts(0).split("#")
            val targetParts = parts(1).split("#")

            // Validate that both sides have class and method names
            if (callerParts.length == 2 && targetParts.length == 2) {

              // Add the parsed suppressed call to the list
              suppressedCalls.addOne(
                SuppressedCall(
                  callerClass = callerParts(0).strip(),
                  callerMethod = callerParts(1).strip(),
                  targetClass = targetParts(0).strip(),
                  targetMethod = targetParts(1).strip()
                )
              )
            }
          }
        }
      }
    } finally {
      source.close() // Always close the file to avoid resource leaks
    }

    suppressedCalls
  }
}

