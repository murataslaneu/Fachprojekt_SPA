package modify.data

import play.api.libs.json._

case class RemovedCall(targetClass: String, targetMethod: String)

object RemovedCall {
  implicit val format: OFormat[RemovedCall] = Json.format[RemovedCall]
}
