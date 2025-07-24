package analyses.B_CriticalMethodsDetector.analysis

import configs.CriticalMethodsDetectorConfig
import _root_.data.SelectedMethodsOfClass
import org.opalj.br.DeclaredMethod
import org.opalj.br.fpcf.properties.Context
import org.opalj.tac.cg.CallGraph

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * Performs analysis to detect invocations of critical methods.
 */
object CriticalMethodsAnalysis {

  /**
   * Analyzes the given project for calls to critical methods.
   *
   * @param callGraph The call graph generated with OPAL.
   * @param config    The config for this analysis.
   * @return A 2-tuple. The first element is a list of warnings as strings, the second a boolean whether at least one
   *         found method call has been suppressed.
   */
  def analyze(callGraph: CallGraph, config: CriticalMethodsDetectorConfig): (List[String], Boolean) = {
    val criticalMethods = config.criticalMethods
    val ignore = config.ignore

    var ignoredCall: Boolean = false
    val warnings = ListBuffer[String]()

    // First look through all reachable method if one of them is critical
    callGraph.reachableMethods().iterator.foreach { context: Context =>
      val methodName = context.method.name
      val declaringClassName = context.method.declaringClassType.toJava
      val isCritical = criticalMethods.exists { classMethods: SelectedMethodsOfClass =>
        classMethods.className == declaringClassName &&
          classMethods.methods.contains(methodName)
      }

      if (isCritical) {
        // Reachable method is critical. Print out all callers

        // Count the number of times the critical method is being called in a method.
        val callersWithCount: mutable.Map[(DeclaredMethod, Boolean), Int] = mutable.Map.empty[(DeclaredMethod, Boolean), Int]
        callGraph.callersOf(context.method).iterator.foreach { elem =>
          val tuple = (elem._1, elem._3)
          callersWithCount(tuple) = callersWithCount.getOrElse(tuple, 0) + 1
        }

        callersWithCount.foreach { tuple =>
          val count = tuple._2
          val callerClassName = tuple._1._1.declaringClassType.toJava
          val callerMethodName = tuple._1._1.name
          val callerDescriptor = tuple._1._1.descriptor
          val isDirect = tuple._1._2
          val shouldIgnore = ignore.exists { ic =>
            ic.callerClass == callerClassName &&
              ic.callerMethod == callerMethodName &&
              ic.targetClass == declaringClassName &&
              ic.targetMethod == methodName
          }

          if (!shouldIgnore) {
            // Warning not ignored, add to result
            if (isDirect) {
              warnings += s"[WARNING] Found $count direct call${if (count != 1) "s" else ""}:\n" +
                s"    To: Class $declaringClassName with method $methodName${context.method.descriptor.toUMLNotation}\n" ++
                s"    In: Class $callerClassName with method$callerMethodName${callerDescriptor.toUMLNotation}"
            } else {
              warnings += s"[WARNING] Found $count indirect call${if (count != 1) "s" else ""}:\n" +
                s"    To: Class $declaringClassName with method $methodName${context.method.descriptor.toUMLNotation}\n" ++
                s"    In: Class $callerClassName with method$callerMethodName${callerDescriptor.toUMLNotation}"
            }
          }
          else {
            ignoredCall = true
          }
        }
      }
    }

    (warnings.toList, ignoredCall)
  }
}
