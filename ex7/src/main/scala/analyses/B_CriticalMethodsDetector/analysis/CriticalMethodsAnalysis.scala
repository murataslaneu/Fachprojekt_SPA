package analyses.B_CriticalMethodsDetector.analysis

import data.{SelectedMethodsOfClass, IgnoredCall}
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
   * @param criticalMethods A list of critical methods to detect.
   * @param suppressedCalls A list of critical method calls that should be suppressed.
   * @return A 2-tuple. The first element is a list of warnings as strings, the second a boolean whether at least one
   *         found method call has been suppressed.
   */
  def analyze(callGraph: CallGraph, criticalMethods: List[SelectedMethodsOfClass],
              suppressedCalls: List[IgnoredCall] = List()): (List[String], Boolean) = {

    var suppressedACall: Boolean = false
    val warnings = ListBuffer[String]()

    // First look through all reachable method if one of them is critical
    callGraph.reachableMethods().iterator.foreach {context: Context =>
      val methodName = context.method.name
      val declaringClassName = context.method.declaringClassType.toJava
      val isCritical = criticalMethods.exists { classMethods: SelectedMethodsOfClass =>
        classMethods.className == declaringClassName &&
          classMethods.selectedMethods.contains(methodName)
      }

      if (isCritical) {
        // Reachable method is critical. Print out all callers

        // Count the number of times the critical method is being called in a method.
        val callersWithCount: mutable.Map[(DeclaredMethod, Boolean), Int] = mutable.Map.empty[(DeclaredMethod, Boolean), Int]
        callGraph.callersOf(context.method).iterator.foreach { elem =>
          val tuple = (elem._1, elem._3)
          callersWithCount(tuple) = callersWithCount.getOrElse(tuple, 0) + 1
        }

        callersWithCount.foreach {tuple =>
          val count = tuple._2
          val callerClassName = tuple._1._1.declaringClassType.toJava
          val callerName = tuple._1._1.name
          val callerDescriptor = tuple._1._1.descriptor
          val isDirect = tuple._1._2
          val isSuppressed = suppressedCalls.exists {sc =>
            sc.callerClass == callerClassName &&
              sc.callerMethod == callerName &&
              sc.targetClass == declaringClassName &&
              sc.targetMethod == methodName
          }

          if (!isSuppressed) {
            // Warning not suppressed, add to result
            if (isDirect) {
              warnings += s"[WARNING] Found $count direct call${if (count != 1) "s" else ""}:\n" +
                s"    To: Class $declaringClassName with method $methodName${context.method.descriptor.toUMLNotation}\n" ++
                s"    In: Class $callerClassName with method$callerName${callerDescriptor.toUMLNotation}"
            } else {
              warnings += s"[WARNING] Found $count indirect call${if (count != 1) "s" else ""}:\n" +
                s"    To: Class $declaringClassName with method $methodName${context.method.descriptor.toUMLNotation}\n" ++
                s"    In: Class $callerClassName with method$callerName${callerDescriptor.toUMLNotation}"
            }
          }
          else suppressedACall = true
        }
      }
    }

    (warnings.toList, suppressedACall)
  }
}
