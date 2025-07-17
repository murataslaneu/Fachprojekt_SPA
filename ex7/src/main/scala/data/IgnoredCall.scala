package data

import play.api.libs.json.{Format, Json}

/**
 * Represents a method call that should be ignored/suppressed from the analysis.
 *
 * @param callerClass The fully qualified name of the class containing the call
 * @param callerMethod The name of the method containing the call
 * @param targetClass The fully qualified name of the target class being called
 * @param targetMethod The name of the target method being called
 */
case class IgnoredCall(callerClass: String, callerMethod: String, targetClass: String, targetMethod: String)

object IgnoredCall {
  implicit val format: Format[IgnoredCall] = Json.format[IgnoredCall]
}