package analyses.F_ArchitectureValidator.data

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}

sealed abstract class AccessType(val name: String)

object AccessType {
  case object METHOD_CALL extends AccessType("METHOD_CALL")

  case object FIELD_ACCESS extends AccessType("FIELD_ACCESS")

  case object INHERITANCE extends AccessType("INHERITANCE")

  case object INTERFACE_IMPLEMENTATION extends AccessType("INTERFACE_IMPLEMENTATION")

  case object FIELD_TYPE extends AccessType("FIELD_TYPE")

  case object RETURN_TYPE extends AccessType("RETURN_TYPE")

  case object PARAMETER_TYPE extends AccessType("PARAMETER_TYPE")

  val values: Seq[AccessType] = Seq(
    METHOD_CALL, FIELD_ACCESS, INHERITANCE,
    INTERFACE_IMPLEMENTATION, FIELD_TYPE,
    RETURN_TYPE, PARAMETER_TYPE
  )

  private def fromName(name: String): Option[AccessType] =
    values.find(_.name == name)


  implicit val format: Format[AccessType] = new Format[AccessType] {
    override def writes(o: AccessType): JsValue = JsString(o.name)

    override def reads(json: JsValue): JsResult[AccessType] = json match {
      case JsString(name) =>
        fromName(name)
          .map(JsSuccess(_))
          .getOrElse(JsError(s"Unknown AccessType: $name"))
      case other => JsError(s"Expected string for AccessType, got: $other")
    }
  }
}
