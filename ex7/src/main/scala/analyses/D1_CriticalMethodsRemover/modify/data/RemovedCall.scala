package modify.data

import play.api.libs.json._

/**
 * Represents a critical method call that was removed from the bytecode.
 *
 * @param targetClass  Fully qualified name of the class containing the critical method.
 * @param targetMethod Name of the method that was removed.
 */
case class RemovedCall(targetClass: String, targetMethod: String)

object RemovedCall {
  // Enables automatic JSON serialization/deserialization for RemovedCall
  implicit val format: OFormat[RemovedCall] = Json.format[RemovedCall]
}
