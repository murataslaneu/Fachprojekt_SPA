package analyses.E_DeadCodeDetector.data

import play.api.libs.json.{Json, OFormat}

/**
 * Class holding a dead instruction (i.e. an instruction that is never accessible during runtime).
 *
 * @param stringRepresentation The instruction in a readable string format (e.g. ALOAD_0)
 * @param programCounter The pc (program counter) value of the instruction inside the method
 * @param foundByDomain List of all domain indexes that found this dead instruction.
 */
case class MultiDomainDeadInstruction
(
  stringRepresentation: String,
  programCounter: Int,
  foundByDomain: List[Int]
)

object MultiDomainDeadInstruction {
  implicit val format: OFormat[MultiDomainDeadInstruction] = Json.format[MultiDomainDeadInstruction]
}
