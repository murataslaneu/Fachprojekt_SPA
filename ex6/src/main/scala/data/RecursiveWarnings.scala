package data

import play.api.libs.json.{Format, JsObject, JsResult, JsSuccess, JsValue, Json, OFormat, OWrites, Writes}

import scala.collection.mutable

case class RecursiveWarnings(
                              warnings: List[String] = Nil,
                              innerWarnings: Map[String, RecursiveWarnings] = Map.empty
                            )

object RecursiveWarnings {
  implicit val format: Format[RecursiveWarnings] = new Format[RecursiveWarnings] {
    override def writes(w: RecursiveWarnings): JsValue = {
      val fields = mutable.Map[String, JsValue]()

      if (w.warnings.nonEmpty)
        fields += "messages" -> Json.toJson(w.warnings)

      if (w.innerWarnings.nonEmpty) {
        val inner: Map[String, JsValue] =
          w.innerWarnings.map { case (k, v) => k -> Json.toJson(v)(format) }
        fields ++= inner
      }

      JsObject(fields)
    }

    override def reads(json: JsValue): JsResult[RecursiveWarnings] = {
      val obj = json.as[JsObject]
      val warnings = (obj \ "messages").asOpt[List[String]].getOrElse(Nil)
      val innerWarnings = obj.fields.collect {
        case (key, value) if key != "messages" =>
          key -> reads(value).get
      }.toMap
      JsSuccess(RecursiveWarnings(warnings, innerWarnings))
    }
  }
}
