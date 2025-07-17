package analyses.B_CriticalMethodsDetector.misc

/**
 * Represents a method call that should be suppressed from the analysis warnings.
 *
 * @param callerClass The fully qualified name of the class containing the call
 * @param callerMethod The name of the method containing the call
 * @param targetClass The fully qualified name of the target class being called
 * @param targetMethod The name of the target method being called
 */
case class SuppressedCall(callerClass: String, callerMethod: String, targetClass: String, targetMethod: String)