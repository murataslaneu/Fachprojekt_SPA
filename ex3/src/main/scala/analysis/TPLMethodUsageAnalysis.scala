package analysis

import org.opalj.br.analyses.Project
import org.opalj.br.{ClassFile, Method}
import java.io.File
import java.net.URL
import scala.collection.mutable

/**
 * Main object responsible for the static analysis of TPL method usage.
 * - For each given TPL (JAR file), finds all its public methods.
 * - Then, determines which of those methods are actually used (called) in the project.
 */
object TPLMethodUsageAnalysis {

  /**
   * Main entry for analyzing TPL usage.
   * @param project OPAL Project object with all loaded class files.
   * @param callGraph Call graph object produced by OPAL (duck-typed).
   * @param tplFiles List of TPL (library) JAR files to analyze.
   * @param callGraphAlgorithm Which call graph algorithm was used (RTA, CHA, etc).
   * @param analysisTimeSeconds Time spent on analysis, for reporting.
   * @return TPLAnalysisResult with all usage statistics.
   */
  def analyze(
               project: Project[URL],
               callGraph: AnyRef,
               tplFiles: List[File],
               callGraphAlgorithm: String,
               analysisTimeSeconds: Double
             ): TPLAnalysisResult = {

    println(s"Starting analysis with ${tplFiles.size} TPL files")
    println(s"CallGraph type: ${callGraph.getClass.getName}")

    val tplInfos: List[TPLInfo] = tplFiles.map { tplFile =>
      val libName = tplFile.getName
      println(s"\n=== Analyzing library: $libName ===")

      // 1. Find all ClassFiles for this TPL by package heuristics (since JAR origin is not always accessible)
      val tplClassFiles = findClassFilesFromLibrary(project, tplFile)
      println(s"Found ${tplClassFiles.size} class files from $libName")

      if (tplClassFiles.isEmpty) {
        println(s"WARNING: No class files found for $libName")
        TPLInfo(libName, 0, 0, 0.0)
      } else {
        // 2. Collect all public methods from those classes
        val allMethods = tplClassFiles
          .flatMap(_.methods)
          .filter(m => m.isPublic)
          .toSet

        println(s"Total public methods in $libName: ${allMethods.size}")

        // 3. Find which of those public methods are actually used (called) in the call graph
        val usedMethods = findUsedMethods(callGraph, tplClassFiles)
        println(s"Used methods in $libName: ${usedMethods.size}")

        val total = allMethods.size
        val used = usedMethods.intersect(allMethods).size
        val ratio = if (total == 0) 0.0 else used.toDouble / total

        println(s"Library: $libName, Total: $total, Used: $used, Ratio: $ratio")
        TPLInfo(libName, total, used, ratio)
      }
    }

    // Return the summary result object for all TPLs
    TPLAnalysisResult(
      analysis = tplInfos,
      callGraphAlgorithm = callGraphAlgorithm,
      analysisTimeSeconds = analysisTimeSeconds
    )
  }

  /**
   * Helper to find all class files belonging to a TPL using package name heuristics.
   * (Direct JAR origin tracking is not always possible in OPAL 5.x)
   */
  private def findClassFilesFromLibrary(project: Project[URL], tplFile: File): List[ClassFile] = {
    val libName = tplFile.getName
    println(s"Searching for classes from library: $libName")

    // Collect all class files from both library and project (as OPAL may store them in either place)
    val allClassFiles = (project.allLibraryClassFiles ++ project.allProjectClassFiles).toList
    println(s"Total class files to check: ${allClassFiles.size}")

    // Filter by likely package prefix for each known library
    val matchedClasses = allClassFiles.filter { cf =>
      isFromLibrary(cf, libName)
    }

    if (matchedClasses.nonEmpty) {
      println(s"Found ${matchedClasses.size} classes for $libName")
      matchedClasses.take(5).foreach(cf => println(s"  Sample class: ${cf.thisType.toJava}"))
    } else {
      println(s"No classes found for $libName")
      // For debug: Print available class files if nothing matched
      if(allClassFiles != null){
        allClassFiles.take(3).foreach(cf => println(s"  Available class: ${cf.thisType.toJava}"))
      } else println("There is nothing here")
    }

    matchedClasses
  }

  /**
   * Heuristic to match a ClassFile to a known TPL (by package name and JAR naming convention).
   */
  private def isFromLibrary(cf: ClassFile, libName: String): Boolean = {
    val className = cf.thisType.toJava

    // Match by major package prefix, adjust for each library as needed
    val isMatch = libName.toLowerCase match {
      case name if name.contains("gson") =>
        className.startsWith("com.google.gson")
      case name if name.contains("guava") =>
        className.startsWith("com.google.common") ||
          className.startsWith("com.google.guava") ||
          className.startsWith("com.google.thirdparty")
      case name if name.contains("jackson") =>
        className.startsWith("com.fasterxml.jackson")
      case name if name.contains("logback") =>
        className.startsWith("ch.qos.logback")
      case name if name.contains("joda") =>
        className.startsWith("org.joda")
      case name if name.contains("pdfbox") =>
        className.startsWith("org.apache.pdfbox")
      case name if name.contains("junit") =>
        className.startsWith("org.junit")
      case name if name.contains("servlet") =>
        className.startsWith("javax.servlet")
      case name if name.contains("scala-library") =>
        className.startsWith("scala.")
      case _ => false
    }

    isMatch
  }

  /**
   * Searches the call graph for any methods belonging to the given TPL classes that are actually used (called).
   */
  private def findUsedMethods(callGraph: AnyRef, tplClassFiles: List[ClassFile]): Set[Method] = {
    val usedMethods = mutable.Set[Method]()
    val tplClassSet = tplClassFiles.toSet

    try {
      val cgClass = callGraph.getClass
      val methods = cgClass.getMethods

      println(s"CallGraph methods: ${methods.map(_.getName).sorted.mkString(", ")}")

      // Try all reasonable call graph accessors (different OPAL versions use different names)
      val methodNames = List("reachableMethods", "calls", "callees", "edges", "allCalls")

      for (methodName <- methodNames) {
        methods.find(_.getName == methodName) match {
          case Some(method) =>
            println(s"Trying method: $methodName")
            try {
              val result = method.invoke(callGraph)
              println(s"$methodName returned: ${result.getClass.getName}")

              result match {
                case iterable: Iterable[_] =>
                  println(s"$methodName returned ${iterable.size} items")

                  iterable.foreach {
                    // Direct Method (unlikely)
                    case m: Method if tplClassSet.contains(m.classFile) =>
                      usedMethods += m
                    case call =>
                      // More likely: a call/edge object holding the target method
                      extractMethodFromCall(call, tplClassSet) match {
                        case Some(m) => usedMethods += m
                        case None =>
                      }
                  }

                  // Stop early if we found any used methods
                  if (usedMethods.nonEmpty) {
                    println(s"Found ${usedMethods.size} used methods via $methodName")
                    return usedMethods.toSet
                  }

                case _ =>
                  println(s"$methodName returned non-iterable result")
              }
            } catch {
              case ex: Exception =>
                println(s"Error calling $methodName: ${ex.getMessage}")
            }
          case None =>
            println(s"Method $methodName not found")
        }
      }

    } catch {
      case ex: Exception =>
        println(s"Error analyzing call graph: ${ex.getMessage}")
        ex.printStackTrace()
    }

    usedMethods.toSet
  }

  /**
   * Attempts to extract a Method object from a call/edge graph node using common field names.
   * (OPAL's call graph edge API may use different names in different versions.)
   */
  private def extractMethodFromCall(call: Any, tplClassSet: Set[ClassFile]): Option[Method] = {
    try {
      val callClass = call.getClass
      val methods = callClass.getMethods

      // Try all likely field/method names
      val fieldNames = List("callee", "target", "method", "to", "calledMethod")

      for (fieldName <- fieldNames) {
        methods.find(_.getName == fieldName) match {
          case Some(method) =>
            val target = method.invoke(call)
            target match {
              case m: Method if tplClassSet.contains(m.classFile) =>
                return Some(m)
              case _ =>
            }
          case None =>
        }
      }

      None
    } catch {
      case _: Exception => None
    }
  }
}