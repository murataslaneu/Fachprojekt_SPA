package analysis

import misc.{CriticalClassMethods, SuppressedCall}
import org.opalj.br.Method
import org.opalj.br.analyses.Project
import org.opalj.br.instructions._
import org.opalj.br.ClassFile
import org.opalj.br.fpcf.properties.Context
import org.opalj.tac.cg.CallGraph

import java.net.URL
import scala.collection.mutable.ListBuffer

/**
 * Performs analysis to detect invocations of critical methods.
 */
object CriticalMethodsAnalysis {

  @deprecated
  /**
   * Analyzes the given project for calls to critical methods.
   *
   * @param project The OPAL project containing class files.
   * @param criticalMethods A list of critical methods to detect.
   * @return A list of warnings as strings.
   */
  def analyzeOld(project: Project[URL], criticalMethods: List[CriticalClassMethods],
              suppressedCalls: List[SuppressedCall] = List()): List[String] = {
    val warnings = ListBuffer[String]()

    // Iterate over all class files in the analyzed project
    for (classFile: ClassFile <- project.allClassFiles) {
      // Retrieve the fully qualified class name (e.g., java.lang.System)
      val className = classFile.fqn

      // Iterate over all methods in the class
      for (method: Method <- classFile.methods if method.body.isDefined) {
        // Skip methods without a defined body (e.g., abstract or interface methods)
        val body = method.body.get
        val methodName = method.name

        // Access the method's instruction list (i.e., bytecode instructions)
        for (instruction <- body.instructions if instruction != null) {
          // Ignore null instructions (may occur due to padding or incomplete code)
          instruction match {
            // Match only instructions that represent method calls (e.g. invokevirtual, invokestatic)
            case m: MethodInvocationInstruction =>
              // Get the class name and method name that is being invoked
              val declaringClass = m.declaringClass.toJava
              val methodName = m.name

              // Check if this call should be suppressed
              val isSuppressed = suppressedCalls.exists { sc =>
                sc.callerClass == className &&
                  sc.callerMethod == methodName &&
                  sc.targetClass == declaringClass
              }

              // Only check for warnings if the call is not suppressed
              if (!isSuppressed) {
                // Check if this call matches any of the user-defined critical methods
                criticalMethods.foreach { cm =>
                  if (cm.className == declaringClass && cm.criticalMethods.contains(methodName)) {
                    // If it matches, add a warning describing where the call was found
                    val warning =
                      s"[WARNING] Found call to $declaringClass.$methodName in $className.${method.name}"
                    warnings += warning
                  }
                }
              }
            case _ => // do nothing for non-invocation instructions
          }
        }
      }
    }
    // Collect all matching warnings and return them as a list
    warnings.toList
  }

  /**
   * Analyzes the given project for calls to critical methods.
   *
   * @param callGraph The call graph generated with OPAL.
   * @param criticalMethods A list of critical methods to detect.
   * @param suppressedCalls A list of critical method calls that should be suppressed.
   * @return A list of warnings as strings.
   */
  def analyze(callGraph: CallGraph, criticalMethods: List[CriticalClassMethods],
              suppressedCalls: List[SuppressedCall] = List()): List[String] = {

    val warnings = ListBuffer[String]()

    // First look through all reachable method if one of them is critical
    callGraph.reachableMethods().iterator.foreach {context: Context =>
      val methodName = context.method.name
      val declaringClassName = context.method.declaringClassType.toJava
      val isCritical = criticalMethods.exists { classMethods: CriticalClassMethods =>
        classMethods.className == declaringClassName &&
          classMethods.criticalMethods.contains(methodName)
      }

      if (isCritical) {
        // Reachable method is critical. Print out all callers
        callGraph.callersOf(context.method).iterator.foreach { tuple =>
          val (caller, _, isDirect) = tuple
          val callerClassName = caller.declaringClassType.toJava
          val callerName = caller.name
          val isSuppressed = suppressedCalls.exists {sc =>
            sc.callerClass == callerClassName &&
              sc.callerMethod == callerName &&
              sc.targetClass == declaringClassName &&
              sc.targetMethod == methodName
          }

          if (!isSuppressed)
            // Warning not suppressed, add to result
            if (isDirect) {
              warnings += s"[WARNING] Found direct call to $declaringClassName.$methodName in $callerClassName.$callerName"
            } else {
              warnings += s"[WARNING] Found indirect call to $declaringClassName.$methodName in $callerClassName.$callerName"
            }
        }
      }
    }

    warnings.toList
  }
}
