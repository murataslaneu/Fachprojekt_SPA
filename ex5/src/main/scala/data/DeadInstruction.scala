package data

import play.api.libs.json.{Json, OFormat}

/**
 * Class holding a dead instruction (i.e. an instruction that is never accessible during runtime)
 * @param stringRepresentation The instruction in a readable string format (e.g. ALOAD_0)
 * @param programCounter The pc (program counter) value of the instruction inside the method
 */
case class DeadInstruction(
                            stringRepresentation: String,
                            programCounter: Int
                          )

object DeadInstruction {
  implicit val format: OFormat[DeadInstruction] = Json.format[DeadInstruction]
}
